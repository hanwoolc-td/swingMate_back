#!/usr/bin/env python3
import json
import re
import shlex
import sys


ALLOWED_TYPES = (
    "Feat",
    "Fix",
    "Refactor",
    "Docs",
    "Test",
    "Chore",
    "Style",
    "Perf",
    "Build",
    "CI",
    "Revert",
    "Hotfix",
    "Design",
    "Merge"
)

COMMIT_PATTERN = re.compile(
    r"^("
    + "|".join(ALLOWED_TYPES)
    + r")(\([A-Za-z0-9._/-]+\))?: (.+)$"
)
KOREAN_PATTERN = re.compile(r"[가-힣]")
GENERIC_DESCRIPTIONS = {
    "수정",
    "변경",
    "업데이트",
    "코드 수정",
    "코드 변경",
    "작업 수정",
    "작업 변경",
    "파일 수정",
    "내용 수정",
    "버그 수정",
}
SHELL_OPERATORS = {"&&", "||", ";", "|", "&"}

EXAMPLES = (
    'Feat: 로그인 기능 추가',
    'Fix: 결제 금액 계산 오류 수정',
    'Refactor: DTO 변환 로직 정리',
    'Fix(payment): 결제 금액 오류 수정',
)


def format_error(reason):
    return (
        "커밋 메시지 규칙 검사에 실패했습니다.\n\n"
        f"사유: {reason}\n\n"
        "올바른 예시:\n"
        + "\n".join(f"- {example}" for example in EXAMPLES)
        + '\n\n사용 형식: git commit -m "Feat: 로그인 기능 추가"'
    )


def deny(reason):
    message = format_error(reason)
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": message,
                }
            },
            ensure_ascii=False,
        )
    )
    return 0


def load_command():
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return ""

    tool_input = payload.get("tool_input") or {}
    if isinstance(tool_input, dict):
        command = tool_input.get("command") or tool_input.get("cmd") or ""
        return command if isinstance(command, str) else ""

    return ""


def parse_tokens(command):
    try:
        lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|")
        lexer.whitespace_split = True
        lexer.commenters = ""
        return list(lexer)
    except ValueError:
        if re.search(r"\bgit\s+commit\b", command):
            raise
        return []


def find_git_commit_args(tokens):
    segment_start = 0

    for index in range(len(tokens) + 1):
        if index < len(tokens) and tokens[index] not in SHELL_OPERATORS:
            continue

        segment = tokens[segment_start:index]
        if len(segment) >= 2 and segment[0] == "git" and segment[1] == "commit":
            return segment[2:]

        segment_start = index + 1

    return None


def extract_inline_message(args):
    messages = []
    index = 0

    while index < len(args):
        arg = args[index]

        if arg in ("-m", "--message"):
            if index + 1 >= len(args):
                return None, f"{arg} 옵션 뒤에 커밋 메시지가 없습니다."
            messages.append(args[index + 1])
            index += 2
            continue

        if arg.startswith("--message="):
            messages.append(arg.split("=", 1)[1])

        index += 1

    if not messages:
        return None, (
            'inline message가 없는 git commit은 검사하기 어렵습니다. '
            'git commit -m "Feat: 설명" 형식을 사용하세요.'
        )

    return "\n\n".join(messages), None


def validate_message(message):
    subject = message.strip().splitlines()[0].strip() if message.strip() else ""
    match = COMMIT_PATTERN.match(subject)

    if not match:
        return (
            "커밋 메시지는 허용 타입과 콜론/공백으로 시작해야 합니다. "
            "예: Fix: 결제 오류 수정"
        )

    description = match.group(3).strip()

    if len(description) < 5:
        return "콜론 뒤 설명은 최소 5자 이상이어야 합니다."

    if description in GENERIC_DESCRIPTIONS:
        return "설명이 너무 일반적입니다. 무엇을 변경했는지 구체적으로 작성하세요."

    if not KOREAN_PATTERN.search(description):
        return (
            "콜론 뒤 설명에는 한글이 최소 1글자 이상 포함되어야 합니다. "
            "영어 개발 용어는 섞어 쓸 수 있지만 전체 영어 문장은 차단됩니다."
        )

    return None


def validate_commit_message_file(path):
    try:
        with open(path, encoding="utf-8") as message_file:
            message = message_file.read()
    except OSError as exc:
        print(f"커밋 메시지 파일을 읽을 수 없습니다: {exc}", file=sys.stderr)
        return 1

    error = validate_message(message)
    if error:
        print(format_error(error), file=sys.stderr)
        return 1

    return 0


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--commit-msg-file":
        return validate_commit_message_file(sys.argv[2])

    command = load_command()
    if not command:
        return 0

    try:
        tokens = parse_tokens(command)
    except ValueError as exc:
        return deny(f"git commit 명령을 파싱할 수 없습니다: {exc}")

    commit_args = find_git_commit_args(tokens)
    if commit_args is None:
        return 0

    message, error = extract_inline_message(commit_args)
    if error:
        return deny(error)

    error = validate_message(message)
    if error:
        return deny(error)

    return 0


if __name__ == "__main__":
    sys.exit(main())
