# Java Provider Secret Injection Baseline

> 日期：2026-06-17
> 范围：J29A
> 状态：已归档

## 目标

让 JDBC provider credential metadata 中的 `secret://...` 引用在后端内部解析，并以 per-run、out-of-band 的 worker secret injection 传给本地 TS worker 进程环境。

## 边界

- 不新增 public raw token API。
- 不把 raw secret 写入 DB、run request JSON、worker JSON、artifact、audit、日志或文档。
- 不实现 KMS、Keychain、file secret、remote runner secret distribution 或完整 secret manager。
- 不改变 `agent-backend-response.v1` 和 TypeScript Agent SDK contract。
- `env://NAME` 继续作为零注入路径工作。
- `secret://...` 只在有后端 resolver 且 worker 明确支持 secret injection 时执行；否则 fail closed。

## 设计

1. 新增 provider secret resolver SPI，用于把 `secret://...` 解析成内存中的 secret value。
2. 新增 `AgentWorkerSecretInjection`，只保存本次 worker 调用要注入的环境变量，不进入 `AgentWorkerRequest`。
3. `AgentRunService` 解析 credential descriptor 时生成 safe env name，例如 `PROVIDER_CREDENTIAL_API_KEY`。
4. `LocalTsAgentWorker` 把 injection 写入 `ProcessBuilder.environment()`，仍只把 `AgentWorkerRequest` 序列化到 stdin。
5. 默认 worker 不支持非空 secret injection；remote HTTP worker 当前不接收 raw secret。

## 验收

- RED：focused Java test 在现有代码上因缺 resolver/injection 能力失败。
- GREEN：`ProviderCredentialRunControllerTest` 覆盖 `secret://` 解析成功且 worker request 无 raw secret/ref。
- Focused：`LocalTsAgentWorkerTest` 覆盖 injected env 不进入 request JSON。
- Full：`mvn -f backend/pom.xml test`、`npm test`、`npm run typecheck`。
- Static：diff check、token/redaction scan、unchecked plan scan。

## 归档证据

- RED：`mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest,LocalTsAgentWorkerTest test` 编译失败，缺 `ProviderSecretResolver`、`AgentWorkerSecretInjection` 和 worker secret injection overload。
- RED：新增 `AgentWorkerSecretInjection.toString()` redaction 断言后，`ProviderCredentialRunControllerTest` 失败，证明默认 record `toString()` 会输出 fake secret value。
- Focused GREEN：同一 focused 命令通过，5 个 Java tests passed。
- Java full：`mvn -f backend/pom.xml test` 通过，104 tests passed。
- TypeScript full：`npm test` 通过，44 files / 178 tests passed。
- TypeScript typecheck：`npm run typecheck` 通过。
