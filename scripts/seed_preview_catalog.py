# 미리보기 카탈로그를 판매 대기 상태로만 추가하는 검토용 SQL을 만든다.
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse, urljoin


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def build_sql(catalog, owner_id, commit=False):
    if type(owner_id) is not int or owner_id < 1:
        raise ValueError('기존 판매자 식별자를 확인해 주세요.')
    if catalog.get('previewOnly') is not True or not isinstance(catalog.get('collections'), list) or not catalog['collections']:
        raise ValueError('미리보기 전용 카탈로그만 등록할 수 있습니다.')
    blocks = []
    titles = set()
    for collection in catalog['collections']:
        title = collection.get('title', '')
        price = collection.get('examplePrice')
        image = urljoin('https://kujishop.shop', collection.get('image', ''))
        products = collection.get('products', [])
        parsed = urlparse(image)
        if not title.startswith('[미리보기] ') or len(title) > 100 or title in titles:
            raise ValueError('미리보기 제목을 확인해 주세요.')
        titles.add(title)
        if type(price) is not int or price < 1 or price > 100000:
            raise ValueError('미리보기 예시 가격을 확인해 주세요.')
        if parsed.scheme != 'https' or parsed.netloc != 'kujishop.shop' or not parsed.path.startswith('/catalog-preview/') or not parsed.path.endswith('.webp') or parsed.query or parsed.fragment or len(image) > 255:
            raise ValueError('직접 제작한 카탈로그 이미지 경로만 사용할 수 있습니다.')
        if not isinstance(products, list) or not 2 <= len(products) <= 10:
            raise ValueError('일반 상품과 라스트상 구성을 확인해 주세요.')
        grades = set()
        for product in products:
            grade, name, quantity = product.get('grade'), product.get('name', ''), product.get('quantity')
            if grade not in ('A', 'B', 'C', 'D', 'E', 'LAST') or grade in grades or not name or len(name) > 100 or type(quantity) is not int or not 1 <= quantity <= 1000:
                raise ValueError('등급·상품명·예시 수량을 확인해 주세요.')
            grades.add(grade)
        if sum(product['quantity'] for product in products if product['grade'] == 'LAST') != 1:
            raise ValueError('라스트상은 일반 수량과 별도로 1개만 구성해야 합니다.')
        block = [f'SELECT id INTO existing_board FROM kujiboard WHERE member_id=owner_id AND title={quote(title)};',
                 'IF existing_board IS NOT NULL THEN',
                 "  IF (SELECT status FROM kujiboard WHERE id=existing_board) <> 'PREPARING' OR EXISTS(SELECT 1 FROM drawhistory WHERE kuji_board_id=existing_board) THEN",
                 "    RAISE EXCEPTION '기존 상품의 상태나 거래 이력은 변경하지 않습니다.';", '  END IF;',
                 'ELSE',
                 f"  INSERT INTO kujiboard(title,price_per_draw,status,member_id,reward_rate,created_at,updated_at) VALUES ({quote(title)},{price},'PREPARING',owner_id,0,current_timestamp,current_timestamp) RETURNING id INTO existing_board;",
                 f'  INSERT INTO kuji_board_image(kuji_board_id,image_url,"sequence",image_type) VALUES (existing_board,{quote(image)},0,\'THUMBNAIL\');']
        for product in products:
            block += [f"  INSERT INTO kujiitem(kuji_board_id,grade,name,total_qty,remain_qty,created_at,updated_at) VALUES (existing_board,{quote(product['grade'])},{quote(product['name'])},{product['quantity']},{product['quantity']},current_timestamp,current_timestamp) RETURNING id INTO new_item;",
                      f'  INSERT INTO kuji_item_image(kuji_item_id,image_url,"sequence") VALUES (new_item,{quote(image)},0);']
        block += ['END IF;']
        blocks.append('\n'.join(block))
    titles_sql = ','.join(quote(title) for title in sorted(titles))
    return '\n'.join([
        '-- 신규 미리보기만 추가하며 기존 상품·거래·포인트·결제 기록은 바꾸지 않는다.',
        'BEGIN;', "SELECT pg_advisory_xact_lock(hashtextextended('oshikuji.preview-catalog.v1',0));",
        'DO $preview_seed$', f'DECLARE owner_id bigint := {owner_id}; existing_board bigint; new_item bigint;', 'BEGIN',
        "IF NOT EXISTS (SELECT 1 FROM member m JOIN business_info b ON b.member_id=m.id WHERE m.id=owner_id AND m.role='BIZ' AND b.status='APPROVED') THEN RAISE EXCEPTION '기존 승인 판매자를 확인해 주세요.'; END IF;",
        f"IF EXISTS (SELECT 1 FROM kujiboard WHERE member_id=owner_id AND title IN ({titles_sql}) GROUP BY title HAVING count(*)>1) THEN RAISE EXCEPTION '같은 이름의 상품이 여러 개여서 중단합니다.'; END IF;",
        *blocks, 'END; $preview_seed$;',
        f"SELECT json_build_object('board',b.id,'title',b.title,'status',b.status,'regular_example_quantity',sum(CASE WHEN i.grade<>'LAST' THEN i.total_qty ELSE 0 END),'last_quantity',sum(CASE WHEN i.grade='LAST' THEN i.total_qty ELSE 0 END),'draw_count',(SELECT count(*) FROM drawhistory d WHERE d.kuji_board_id=b.id)) FROM kujiboard b JOIN kujiitem i ON i.kuji_board_id=b.id WHERE b.member_id={owner_id} AND b.title IN ({titles_sql}) GROUP BY b.id ORDER BY b.id;",
        'COMMIT;' if commit else 'ROLLBACK;'
    ])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='미리보기 전용 SQL 생성. 기본 결과는 ROLLBACK이며 데이터베이스에 직접 접속하지 않습니다.')
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--owner-id', type=int, required=True)
    parser.add_argument('--commit', action='store_true', help='검토한 SQL의 마지막을 COMMIT으로 생성합니다.')
    args = parser.parse_args()
    print(build_sql(json.loads(args.catalog.read_text(encoding='utf-8')), args.owner_id, args.commit))
