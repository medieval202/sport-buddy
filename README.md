
# 动伴：大学生运动智能匹配搭子系统

这是一个可直接运行和演示的 Spring Boot 单体原型，围绕“找到合拍的运动搭子”和“安全预约校园场地”两个核心场景设计。

## 快速启动

环境要求：JDK 17。

最直接的演示方式是双击项目目录中的 `run.cmd`，或在本目录执行：

```powershell
.\run.cmd
```

启动完成后，用浏览器打开 <http://127.0.0.1:8081>。`run.cmd` 使用独立端口 `8081`，避免与常见的 8080 项目冲突。

使用 Maven 从源码启动的方式：

```powershell
.\mvnw.cmd spring-boot:run
```

源码开发模式默认浏览器地址为 <http://127.0.0.1:8080>。

如果系统已安装 Maven 3.9+，也可以运行 `mvn spring-boot:run`。Maven Wrapper 会在首次运行时自动下载 Maven。

演示账户：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 学生 | `demo` | `demo123` |
| 管理员 | `admin` | `admin123` |

其他学生演示账户为 `lin/lin123`、`xiaoyu/xiaoyu123`、`haoran/haoran123`、`yue/yue123`。

雷达匹配还提供 `zixin`、`yifan`、`zhoumeng`、`wangjun` 四个模拟候选账户，密码均为 `demo123`。学生端“雷达匹配”页面默认开启演示候选在线，关闭后只匹配真实在线用户。

原型使用 H2 内存数据库，每次重启会恢复演示数据。使用 `run.cmd` 启动后，H2 控制台地址为 <http://127.0.0.1:8081/h2-console>，JDBC URL 是 `jdbc:h2:mem:sportsbuddy`。

## 雷达匹配演示步骤

1. 使用 `demo / demo123` 登录学生端。
2. 进入左侧“雷达匹配”，选择“跑步”和“现在”或“晚上”。
3. 保持“启用演示候选在线”，点击“启动扫描”。
4. 观察八个扇区每 5 秒轮询、候选光点出现、Top-N 动态重排和 WebSocket 状态推送。
5. 锁定候选后点击“发起邀约”，系统会暂停扫描并写入一条真实的组队邀约记录。
6. 可点击“模拟候选失联”观察自动补位，或关闭演示候选在线，与另一名真实登录学生进行匹配。

## 已实现功能

- 学生注册、JWT 登录、个人资料与运动偏好维护
- 学生发布活动、管理员审核、公开活动浏览、加入和退出
- 五维智能匹配：运动项目 35 分、可用时间 25 分、距离 20 分、水平 10 分、信用 10 分
- 实时找搭子：在线学生提交匹配意图后立即撮合，搜索期间每 5 秒通过 WebSocket 推送在线数、搜索人数和等待时长
- 雷达式匹配：8 扇区轮询、Top-N 动态结算、候选失联补位、锁定候选并创建邀约记录
- 向匹配候选人发送站内实时邀请
- 校园场地查询、按时段预约、取消预约
- 使用数据库悲观锁串行化同场地预约，并检查时间重叠
- 管理员用户列表、信用分调整和账户启停
- 管理员公告发布，WebSocket 实时推送活动审核结果、加入通知和公告
- 响应式中文 Web 界面，PC 和手机浏览器均可演示

## 技术结构

| 层次 | 技术 |
| --- | --- |
| Web 与页面 | Spring MVC、原生 HTML/CSS/JavaScript |
| 数据访问 | Spring Data JPA、Hibernate |
| 数据库 | H2 内存数据库 |
| 安全 | Spring Security、JWT、BCrypt |
| 实时通知 | Spring WebSocket |
| 测试 | JUnit 5、MockMvc |

主要代码目录：

```text
src/main/java/com/campus/sportsbuddy
├── config       安全、WebSocket、演示数据初始化
├── controller   REST API
├── dto          请求与响应对象
├── model        JPA 实体和枚举
├── repository   Spring Data JPA 仓储
├── security     JWT 过滤器与用户认证
├── service      业务规则与事务
└── websocket    在线会话和通知推送
```

## 匹配算法

候选人必须启用、满足活动最低信用分，且不是创建者或已加入成员。最终按总分降序取前 10 人。

| 维度 | 权重 | 量化方式 |
| --- | ---: | --- |
| 运动项目 | 35 | 候选人偏好包含活动项目得 35 分 |
| 时间窗 | 25 | 活动时段位于候选人可用时段，或周末活动命中 `WEEKEND`，得 25 分 |
| 物理距离 | 20 | 使用 Haversine 公式计算，≤1km 得 20，≤3km 得 17，≤5km 得 14，≤10km 得 8 |
| 运动水平 | 10 | 完全一致得 10，相差一级得 6，相差两级得 2 |
| 信用分 | 10 | `min(10, 信用分 / 10)` |

时间窗定义：`MORNING` 为 12 点前，`AFTERNOON` 为 12:00-18:00，`EVENING` 为 18:00 后。距离根据用户和活动地点经纬度计算。

实时匹配池会优先寻找运动项目和时间窗完全一致的在线学生，再使用距离、水平和信用分计算综合匹配度。匹配意图有效期为 30 分钟，在线状态通过 WebSocket 心跳维护，超过 2 分钟未活跃的用户不会参与实时撮合。等待过程中，服务端每 5 秒推送一次搜索状态，网页据此展示雷达旋转、目标闪烁和脉冲扩散动画。

## 并发预约控制

预约事务会先对 `venues` 表的目标场地记录执行 `SELECT ... FOR UPDATE`，让同一场地的并发请求串行检查。随后查询同日已确认预约，使用半开区间判断：

```text
新预约开始 < 已有预约结束
且
新预约结束 > 已有预约开始
```

发生重叠时返回 HTTP 409。该方案能在单数据库事务下防止同场地时段被重复占用，生产环境还应配合数据库约束、限流和监控。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 学生注册 |
| POST | `/api/auth/login` | 登录并返回 JWT |
| GET/PUT | `/api/me` | 查看或修改个人档案 |
| GET/POST | `/api/activities` | 查询公开活动 / 提交活动 |
| GET | `/api/activities/mine` | 我的活动 |
| POST/DELETE | `/api/activities/{id}/join` | 加入 / 退出活动 |
| GET | `/api/activities/{id}/matches` | 获取智能匹配候选 |
| GET | `/api/realtime/status` | 查询在线数、搜索数和当前匹配 |
| POST | `/api/realtime/matches` | 进入实时匹配池 |
| DELETE | `/api/realtime/matches/current` | 取消或结束当前匹配 |
| POST | `/api/radar/sessions` | 启动雷达扫描会话 |
| GET | `/api/radar/sessions/current` | 获取当前扫描、候选和锁定状态 |
| POST | `/api/radar/sessions/{sessionId}/candidates/{candidateId}/lock` | 锁定候选并暂停扫描 |
| POST | `/api/radar/sessions/{sessionId}/resume` | 恢复扫描 |
| POST | `/api/radar/sessions/{sessionId}/candidates/{candidateId}/engage` | 创建搭子邀约 |
| POST | `/api/radar/sessions/{sessionId}/simulate-event` | 模拟候选失联并触发补位 |
| DELETE | `/api/radar/sessions/{sessionId}` | 关闭扫描会话 |
| POST | `/api/radar/matches/{matchId}/respond?accept=` | 接受或拒绝搭子邀约 |
| POST | `/api/activities/{id}/invite/{userId}` | 发送邀请通知 |
| GET | `/api/venues` | 查询可用场地 |
| GET | `/api/venues/{id}/busy?date=` | 查询当日占用时段 |
| POST/DELETE | `/api/reservations` | 创建 / 取消预约 |
| GET/PATCH | `/api/admin/activities/...` | 管理员查询和审核活动 |
| GET/PATCH | `/api/admin/users/...` | 管理员管理学生 |
| POST | `/api/admin/announcements` | 管理员发布公告 |

## 运行测试

```powershell
.\mvnw.cmd test
```

测试覆盖登录鉴权、活动提交审核与加入、智能匹配、实时撮合、雷达扫描与邀约、预约冲突拦截以及学生越权访问管理员接口。

## 原型边界

当前版本用于课程设计和功能演示，未实现活动签到、复杂信用流水、消息离线历史、管理员场地维护、找回密码和完整的前端工程化。生产化时应把 JWT 密钥放入环境变量、使用持久化数据库、增加数据库级排他约束并接入日志审计。
