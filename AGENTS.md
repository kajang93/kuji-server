# AGENTS.md (AI 전역 규칙 및 행동 지침)

Behavioral guidelines for AI coding agents (Codex, Claude, etc.). This file integrates global AI behaviors and project-specific instructions for the OshiKuji Client project.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 🎯 Project-Specific Rules (OshiKuji Client)

### 1. 커밋 메시지 규칙 (가장 중요)
- 모든 커밋 메시지는 **반드시 100% 한글**로만 작성하세요.
- `feat:`, `fix:`, `chore:`, `README:` 등 영어로 된 접두사를 **절대 사용하지 마세요**.
- 변경 사항을 한글로 명확하게 풀어서 설명하세요. (예: "회원가입 문자 인증 연동 완료", "리드미 문서 기능 업데이트")

### 2. 스타일링 및 UI 규칙
- 프리미엄 UI/UX 디자인을 최우선으로 적용하세요.
- `Tailwind CSS`를 기본으로 사용하며, 애니메이션은 `Framer Motion`, 아이콘은 `Lucide React`를 사용하세요.
- 다크 모드를 기본으로 하며, 배경은 `Slate-900`에서 `Indigo-950` 색상(또는 `Deep Black`)을, 강조 색상은 `Rose`, `Amber`, `Cyan` 등을 사용하세요.

### 3. 코드 작성 스타일
- `TypeScript`를 기반으로 한 함수형 컴포넌트(`React.FC`)를 사용하세요.
- API 키나 민감한 정보는 절대 코드에 하드코딩하지 말고 환경 변수(`.env`)를 사용하세요.

---

## 🤖 AI Behavioral Guidelines

### 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

### 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

### 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**
- Transform tasks into verifiable goals (e.g., "Fix the bug" -> "Write a test that reproduces it, then make it pass").
- Strong success criteria let you loop independently. Weak criteria require constant clarification.

### 5. Workspace Evidence Before Edits
**Inspect the actual files you will touch. Don't rely on memory or stale summaries.**
- Use project tools to find the relevant implementation.
- Read the exact files and nearby call sites before editing them.
- Treat open editor tabs, filenames, READMEs as hints, not proof.

### 6. Respect The Worktree
**Assume uncommitted changes belong to the user unless you made them.**
- Do not revert, overwrite, or reformat unrelated changes.
- If user changes touch the same files, read them and adapt.
- Never run destructive git commands unless explicitly asked.

### 7. No Closing Colons (Korean Output)
**End Korean sentences with a period, not a colon.**
- When the user writes in Korean, your output is also Korean.
- Don't end Korean sentences with `:` even if the next line is a list or example. (Use `.`, `?`, or `!`).

### 8. File Header Comments in Korean
**First line of every new source file: a one-line Korean comment stating its role.**
- `// 사용자 인증 상태를 관리하는 Context Provider`
- Place it directly under required directives (`'use client'`).
- Skip config files.

### 9. Plan + Checklist + Context Notes
**Before any non-trivial task, produce artifacts. Don't start coding without them.**
- **Plan** - what we're building and why.
- **Checklist** (`checklist.md`) - concrete tasks as checkboxes.
- **Context Notes** (`context-notes.md`) - decisions made during the work.

**⚠️ 중요: `checklist.md`와 `context-notes.md` 등 계획/작업 추적용 임시 아티팩트 파일들은 절대 Git에 커밋하지 마세요.**

### 10. Run Tests Before Marking Complete
**If you touched code, run the relevant tests before saying "done".**
- Run the smallest relevant check first.
- If no test setup exists, verify the project builds or typechecks.
- This is non-negotiable.

### 11. Verification Evidence In The Final Reply
**Report what you actually verified, not what you intended to verify.**
- Include the command or check that ran.
- Include the result.
- Do not write "done" unless backed by a concrete check.

### 12. Semantic Commits
**Commit when one logical change is complete. Don't wait for the user to ask.**
- Keep it to one meaningful logical change.
- Never create a commit automatically unless it passes the "describe in one sentence" test.
*(Note: Project specific rule overrides prefix usage: DO NOT use english prefixes like feat: or fix: for this project.)*

### 13. Read Errors, Don't Guess
**Read the actual error/log line. Don't pattern-match from memory.**
- Read the full error message and stack trace.
- Check the actual log output.
- Don't apply a "common fix" before confirming the cause.

---
**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, verification is reported with exact checks, and clarifying questions come before implementation rather than after mistakes.
