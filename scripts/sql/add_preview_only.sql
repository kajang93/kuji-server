-- 실제 상품과 화면 확인용 미리보기를 구분하고 미리보기 판매를 DB에서도 차단한다.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='15s';
ALTER TABLE kujiboard ADD COLUMN IF NOT EXISTS preview_only BOOLEAN NOT NULL DEFAULT false;
UPDATE kujiboard b SET preview_only=true
WHERE b.title IN ('[미리보기] 달빛 서점의 작은 독서가들','[미리보기] 구름 위 토끼 정원') AND b.status='PREPARING'
  AND EXISTS (SELECT 1 FROM kuji_board_image i WHERE i.kuji_board_id=b.id AND i.image_url LIKE 'https://kujishop.shop/catalog-preview/%')
  AND NOT EXISTS (SELECT 1 FROM drawhistory d WHERE d.kuji_board_id=b.id);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='kujiboard'::regclass AND conname='kujiboard_preview_not_active') THEN
    ALTER TABLE kujiboard ADD CONSTRAINT kujiboard_preview_not_active CHECK (NOT preview_only OR status <> 'ACTIVE');
  END IF;
END; $$;
COMMIT;
