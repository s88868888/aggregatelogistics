# Spring Boot Starter 使用说明

本项目已改造为 Spring Boot Starter，同时保留原有的非 Spring Boot 使用方式。

## Spring Boot 项目接入

### 1. 安装到本地 Maven 仓库

```bash
mvn clean install -Dgpg.skip=true
```

### 2. 引入依赖

```xml
<dependency>
    <groupId>net.kdks</groupId>
    <artifactId>aggregatelogistics</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. application.yml 配置

```yaml
aggregate-logistics:
  # 顺丰
  shunfeng:
    partner-id: your-partner-id
    request-id: your-request-id
    check-word: your-check-word
    is-product: 0          # 0=沙箱 1=生产
  # 中通
  zhongtong:
    app-key: your-app-key
    secret-key: your-secret-key
    is-product: 1
  # 圆通
  yuantong:
    appkey: your-appkey
    secret-key: your-secret-key
    user-id: your-user-id
    is-product: 1
  # 申通（可选）
  shentong:
    appkey: your-appkey
    secret-key: your-secret-key
    is-product: 1
  # 极兔（可选）
  jitu:
    api-account: your-api-account
    private-key: your-private-key
    is-product: 1
  # 韵达（可选）
  yunda:
    app-key: your-app-key
    app-secret: your-app-secret
    is-product: 1
  # 京东（可选）
  jingdong:
    app-key: your-app-key
    app-secret: your-app-secret
    access-token: your-access-token
    customer-code: your-customer-code
    is-product: 1
```

只需配置你要用的快递，未配置的不会注册。

### 4. 注入使用

```java
@RestController
@RequestMapping("/logistics")
public class LogisticsController {

    @Autowired
    private ExpressHandlers expressHandlers;

    /**
     * 下单
     */
    @PostMapping("/createOrder")
    public ExpressResponse<OrderResult> createOrder(@RequestParam String companyCode,
                                                    @RequestBody CreateOrderParam param) {
        return expressHandlers.createOrder(param, companyCode);
    }

    /**
     * 查询轨迹
     */
    @PostMapping("/track")
    public ExpressResponse<List<ExpressResult>> track(@RequestParam String companyCode,
                                                      @RequestBody ExpressParam param) {
        return expressHandlers.getExpressInfo(param, companyCode);
    }

    /**
     * 查询已注册的快递公司
     */
    @GetMapping("/supported")
    public Set<String> supported() {
        return expressHandlers.getSupportedCodes();
    }
}
```

### 5. 快递公司编码

| 编码 | 快递公司 | 下单 | 轨迹 | 运费 |
|------|---------|------|------|------|
| SF   | 顺丰速运 | ✅   | ✅   | ❌   |
| ZTO  | 中通快递 | ✅   | ✅   | ✅   |
| YTO  | 圆通速递 | ✅   | ✅   | ✅   |
| STO  | 申通快递 | ❌   | ✅   | ✅   |
| HTKY | 百世快递 | ❌   | ✅   | ❌   |
| JT   | 极兔速递 | ❌   | ✅   | ✅   |
| YD   | 韵达快递 | ❌   | ✅   | ❌   |
| JD   | 京东物流 | ❌   | ✅   | ❌   |

---

## 非 Spring Boot 项目（原有方式不变）

```java
ExpressConfig config = ExpressConfig.builder()
    .shunfengConfig("partnerId", "requestId", "checkWord", 1)
    .zhongtongConfig("companyId", "secretKeyV1", "appKey", "secretKey", "v2", 1)
    .yuantongConfig("appkey", "secretKey", "userId", 1)
    .build();
ExpressHandlers expressHandlers = new ExpressHandlers(config);

// 下单
ExpressResponse<OrderResult> result = expressHandlers.createOrder(createOrderParam, "SF");

// 查轨迹
ExpressResponse<List<ExpressResult>> info = expressHandlers.getExpressInfo(expressParam, "ZTO");
```

---

## 本次改造内容

1. **pom.xml** — 新增 `spring-boot-autoconfigure`（optional）
2. **新增 `net.kdks.autoconfigure`** — `LogisticsProperties` + `LogisticsAutoConfiguration`
3. **新增 `META-INF/spring.factories`** — Boot 2.x 自动装配注册
4. **新增 `META-INF/spring/...AutoConfiguration.imports`** — Boot 3.x 自动装配注册
5. **修复 `ExpressHandlers.getSupportedCode`** — 去掉静默降级到申通的 bug，改为直接报错
6. **新增 `ExpressHandlers.getSupportedCodes()`** — 查询已注册快递
7. **实现中通下单** — `ExpressZhongtongHandler.createOrder` + `ZhongtongRequest.createOrderRequest`
8. **实现圆通下单** — `ExpressYuantongHandler.createOrder` + `YuantongRequest.createOrderRequest`
9. **新增常量** — `ZhongtongMethod.CREATE_ORDER_URL` + `YuantongMethod.CREATE_ORDER` / `CREATE_ORDER_URL`
