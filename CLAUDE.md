# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 工作规则

- **语言**: 所有会话必须使用中文回复。
- **Shell**: 执行命令时统一使用 PowerShell，不要使用 Cmd。
- **记忆保存**: 每次对话结束后，将关键决策、用户偏好和项目进展保存到 `C:\Users\ALEN\.claude\projects\D--alenwifidata\memory\` 目录下的记忆文件中。
- **先计划后执行**: 在进行任何非简单的代码修改或操作之前，先列出计划步骤，获得用户同意后再执行。
- **提交后操作**: 每次 Git 提交完成后，需要：
  1. 总结本次提交的工作日志（包含提交内容、变更文件、影响范围）。
  2. 同步更新远程仓库的 `README.md`，使其反映项目最新状态（新增功能、文件结构、使用说明等）。
