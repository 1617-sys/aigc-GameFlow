# 性能测试

Compose 会强制使用 Mock Provider，不会调用付费接口。

## 1. 准备多用户 Token

单用户默认只能提交 5 次/秒且初始余额为 10，不能用于证明全局吞吐。启动系统后执行：

```powershell
.\performance\prepare-users.ps1 -Count 100 -Balance 100000
```

脚本注册独立的压测用户，将余额提高后，把 JWT 写入 `performance/tokens.csv`。用户名包含本次运行时间戳，脚本会输出前缀，测试结束后可以按前缀精准清理。

## 2. 执行测试

```powershell
jmeter -n -t performance/generation-submit.jmx `
  -JtokensFile=performance/tokens.csv `
  -Jthreads=100 `
  -Jramp=10 `
  -Jduration=60 `
  -l performance/results.jtl `
  -e -o performance/report
```

JMeter 在全部线程之间循环使用 Token，并为每个请求生成不同的 `Idempotency-Key`。默认全局限流为每秒 300 次，因此超过部分返回 429 属于预期降级，不应算作服务内部错误。

## 3. 报告口径

至少记录：机器配置、App 实例数、连接池参数、线程数、持续时间、成功 RPS、429/503 数量、P95、P99、500 错误率、RabbitMQ 队列峰值和 Worker 完成吞吐。

`tokens.csv`、`results.jtl` 和 HTML 报告目录均不应提交 Git。
