# NutriMind 组员本地部署与协作指南

这份文档写给第一次接触这个项目、甚至第一次接触 Git 和微服务的同学。你不需要一开始就完全理解所有概念，先按步骤把项目跑起来，再逐步熟悉就可以。

## 1. 先理解这个项目是什么

NutriMind 是一个微服务项目。所谓“微服务”，你可以先简单理解成：

- 用户相关功能在一个服务里
- 食物库相关功能在一个服务里
- 饮食记录、社区、顾问相关功能在一个服务里
- AI 识别能力在一个服务里
- 网关负责把前端请求转发到对应服务

这样做的好处是，每个服务只负责一部分功能，后面开发时不容易把所有逻辑都堆在一个项目里。

本项目主要服务如下：

- `gateway-service`
  作用：统一入口，前端通常访问它，再由它转发到其他服务。
- `user-service`
  作用：用户注册、登录、用户资料、健康目标、体重记录。
- `food-service`
  作用：食物基础库，给手动记录和 AI 识别后的匹配提供食物数据。
- `meal-service`
  作用：饮食记录、饮食计划、营养顾问、社区内容。
- `ai-service`
  作用：食物识别接口和与 Python 推理服务的对接。

## 2. 每个服务使用哪些数据库表

### 2.1 `gateway-service`

`gateway-service` 不连数据库。

它只是路由转发，不负责保存业务数据。

### 2.2 `ai-service`

`ai-service` 当前主工程不连 MySQL。

它主要依赖：

- `food-service` 提供食物库查询
- Python 推理服务提供识别结果

所以它没有自己的 `schema.sql`，也没有自己的业务表。

### 2.3 `user-service`

`user-service` 使用数据库：`nutrimind_user`

表如下：

- `user_account`
  作用：用户账号、密码、昵称、邮箱、手机号、角色、状态。
- `user_profile`
  作用：用户健康资料，比如性别、生日、身高、饮食偏好、过敏原。
- `health_goal`
  作用：健康目标，比如减脂、增肌、目标热量、目标蛋白质、目标体重。
- `weight_log`
  作用：体重记录。

表结构来源：

- [user-service schema.sql](/d:/workspace/NutriMind/user-service/src/main/resources/schema.sql#L1)

### 2.4 `food-service`

`food-service` 使用数据库：`nutrimind_food`

表如下：

- `food_basics`
  作用：食物基础信息，包括名称、分类、条码、单位、热量、蛋白质、脂肪、碳水、膳食纤维等。

另外，这个服务启动时还会自动补充一批演示食物数据，方便识别和手动记录直接使用。

相关代码：

- [food-service schema.sql](/d:/workspace/NutriMind/food-service/src/main/resources/schema.sql#L1)
- [FoodCatalogBootstrap.java](/d:/workspace/NutriMind/food-service/src/main/java/com/yxw/food/config/FoodCatalogBootstrap.java#L18)

### 2.5 `meal-service`

`meal-service` 使用数据库：`nutrimind_meal`

表如下：

- `meal_record`
  作用：某个用户某一天的饮食汇总。
- `meal_detail`
  作用：某一餐中具体吃了什么、吃了多少。
- `advisor_message`
  作用：营养顾问聊天记录。
- `community_post`
  作用：社区帖子。
- `community_post_like`
  作用：帖子点赞记录。
- `community_comment`
  作用：帖子评论。
- `post_favorite`
  作用：帖子收藏。
- `meal_plan`
  作用：每日饮食计划。
- `meal_plan_item`
  作用：饮食计划中的具体项目。

这个服务启动时还会自动补充一批演示社区帖子。

相关代码：

- [meal-service schema.sql](/d:/workspace/NutriMind/meal-service/src/main/resources/schema.sql#L1)
- [CommunityBootstrap.java](/d:/workspace/NutriMind/meal-service/src/main/java/com/yxw/meal/config/CommunityBootstrap.java#L12)

## 3. 这些数据库和表是怎么创建的

这是最容易混淆的地方，建议你记住下面这句话：

数据库名和表，不是完全同一层。

- “数据库”指的是 `nutrimind_user`、`nutrimind_food`、`nutrimind_meal`
- “表”指的是这些数据库里的 `user_account`、`food_basics`、`meal_record` 之类

### 3.1 数据库名是谁创建的

如果你使用项目自带的 Docker MySQL，那么数据库名由下面这个脚本自动创建：

- [docker/mysql/init/01-init-databases.sql](/d:/workspace/NutriMind/docker/mysql/init/01-init-databases.sql#L1)

它会创建：

- `nutrimind_user`
- `nutrimind_food`
- `nutrimind_meal`

注意：

- 这类 `docker-entrypoint-initdb.d` 脚本，通常只会在 MySQL 容器“第一次初始化数据目录”时执行。
- 如果 MySQL 的 Docker 数据卷已经存在，再次 `up -d` 不会重新执行这份建库脚本。

### 3.2 表是谁创建的

表由各个服务启动时自动执行自己的 `schema.sql` 来创建。

原因是这三个服务都配置了：

- [user-service application.properties:10](/d:/workspace/NutriMind/user-service/src/main/resources/application.properties#L10)
- [food-service application.properties:10](/d:/workspace/NutriMind/food-service/src/main/resources/application.properties#L10)
- [meal-service application.properties:10](/d:/workspace/NutriMind/meal-service/src/main/resources/application.properties#L10)

对应配置是：

- `spring.sql.init.mode=always`

这意味着：

- 服务启动时会执行 `src/main/resources/schema.sql`
- 如果表不存在，就会自动创建
- 因为 SQL 里大多使用了 `CREATE TABLE IF NOT EXISTS`，重复启动通常不会重复报错

### 3.3 那我到底要不要手动建库

分两种情况：

#### 情况 A：你使用项目自带 Docker MySQL

推荐新同学优先用这种方式。

你通常不需要手动建库。

原因：

- Docker MySQL 第一次启动时会执行建库脚本
- 各个业务服务启动时会自动建表

也就是说：

- 数据库自动建
- 表自动建

#### 情况 B：你不用 Docker MySQL，而是用自己电脑上的 MySQL

这种情况下，你至少要确保数据库已经存在：

- `nutrimind_user`
- `nutrimind_food`
- `nutrimind_meal`

因为服务会自动建表，但前提是它连上的数据库本身已经存在。

如果数据库不存在，服务启动时会连库失败。

## 4. 推荐的本地部署方式

对 0 基础同学，推荐统一使用下面这套方案：

- 用 Docker 启动基础设施
- 用脚本启动 Java 服务
- 用 `.env` 管理本地敏感配置

这样大家的环境最接近，问题也最容易排查。

### 4.1 到底是哪一个 MySQL 在生效

这是你们后面最常会问到的问题，可以直接记下面两句话：

- `MYSQL_HOST` 和 `MYSQL_PORT` 决定“服务实际要连哪一个 MySQL”
- `MYSQL_DOCKER_PORT` 决定“Docker 里的 MySQL 暴露到你电脑哪个端口”

项目现在的推荐默认值是：

- 本机自己装的 MySQL：`3306`
- 项目 Docker MySQL：`3307`

也就是说：

- 如果 `.env` 里写的是 `MYSQL_PORT=3307`，项目会去连 Docker MySQL
- 如果 `.env` 里写的是 `MYSQL_PORT=3306`，项目会去连你电脑本机的 MySQL

而 `docker-compose.dev.yml` 里的 MySQL 容器默认会把：

- 容器内部的 `3306`
- 映射到你电脑的 `3307`

所以推荐你们团队统一先按 Docker MySQL 来跑，这样最省心。

## 5. 第一次在本地跑项目的完整步骤

### 5.1 需要先安装什么

请先安装：

1. Git
2. JDK 17
3. Docker Desktop
4. 一个能打开这个项目的 IDE
   例如 IntelliJ IDEA

如果你只会很基础的操作也没关系，照着下面一步一步做就行。

### 5.2 克隆项目

在终端执行：

```powershell
git clone <你的仓库地址>
cd NutriMind
```

### 5.3 创建本地配置文件

把根目录的 `.env.example` 复制成 `.env`：

```powershell
Copy-Item .env.example .env
```

然后打开 `.env`，至少填写下面几个值：

- `APP_JWT_SECRET`
- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`

建议：

- `APP_JWT_SECRET` 填一个至少 32 位的随机字符串
- 如果你要使用项目 Docker MySQL，`MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 可以先填成一样的值

示例：

```env
APP_JWT_SECRET=0123456789abcdef0123456789abcdef
APP_JWT_EXPIRE=86400000

MYSQL_HOST=localhost
MYSQL_PORT=3307
MYSQL_DOCKER_PORT=3307
MYSQL_USERNAME=root
MYSQL_PASSWORD=12345678
MYSQL_ROOT_PASSWORD=12345678
MYSQL_DB=nutrimind_user
MYSQL_FOOD_DB=nutrimind_food
MYSQL_MEAL_DB=nutrimind_meal

SPRING_PROFILES_ACTIVE=discovery
NACOS_SERVER_ADDR=127.0.0.1:8848

APP_RAG_MILVUS_URI=http://localhost:19530
APP_RAG_QWEN_API_KEY=
```

说明：

- `APP_RAG_QWEN_API_KEY` 可以暂时留空
- 留空后 `meal-service` 仍然可以启动，只是 Milvus 向量检索增强不会完全启用
- `MYSQL_PORT=3307` 表示服务默认连接项目 Docker MySQL
- 如果你以后想改用本机自己装的 MySQL，把 `MYSQL_PORT` 改成 `3306` 即可

### 5.4 启动基础设施

如果你现在只是想先把 MySQL 跑起来，执行：

```powershell
docker compose -f docker-compose.dev.yml up -d mysql
```

如果你想把项目需要的基础设施都一起启动，执行：

```powershell
docker compose -f docker-compose.dev.yml up -d
```

这一步会启动：

- MySQL
- Redis
- Milvus
- Nacos

最常用的检查命令如下：

```powershell
docker ps
docker logs nutrimind-mysql --tail 50
docker compose -f docker-compose.dev.yml stop mysql
```

它们分别表示：

- `docker ps`：查看容器有没有真的跑起来
- `docker logs nutrimind-mysql --tail 50`：查看 MySQL 最近 50 行日志
- `docker compose -f docker-compose.dev.yml stop mysql`：只停止 MySQL 容器

你第一次启动 MySQL 时，如果看到初始化过程持续十几秒到几十秒，通常是正常的。

### 5.5 启动微服务

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-discovery.ps1
```

这个脚本会：

- 自动读取 `.env`
- 自动读取 `.env.local`
- 校验你有没有填写必要的环境变量
- 启动 `user-service`
- 启动 `food-service`
- 启动 `meal-service`
- 启动 `ai-service`
- 启动 `gateway-service`

脚本位置：

- [start-local-discovery.ps1](/d:/workspace/NutriMind/scripts/start-local-discovery.ps1#L1)

### 5.6 启动后怎么看是否成功

你可以检查这些地址：

- Nacos 控制台：`http://localhost:8848/nacos`
- 网关测试入口：`http://localhost:8080`
- 用户服务：`http://localhost:8081`
- 食物服务：`http://localhost:8082`
- 餐食服务：`http://localhost:8083`
- AI 服务：`http://localhost:8084`
- 网关：`http://localhost:8080`

如果某个端口起不来，最常见原因是：

- 端口被占用了
- `.env` 没填完整
- MySQL 密码不对
- 本机已有 MySQL 占用了 3306，而你又把 Docker MySQL 也映射到了 3306

## 6. 常见问题

### 6.1 我已经执行了 Docker，但还是没有数据库

先确认是不是第一次初始化。

如果 MySQL 的 Docker 数据卷已经存在，那么：

- `01-init-databases.sql` 不会自动重新执行

这种情况下你可以：

- 手动进入 MySQL 建库
- 或者在确认数据不重要的前提下删除 MySQL 数据卷后重新初始化

### 6.2 为什么我的服务启动了，但表还是不对

因为“数据库存在”不等于“表结构完全正确”。

表结构依赖每个服务自己的 `schema.sql`。

只要数据库存在，服务启动时就会尝试自动建表或补列。

### 6.3 为什么 `meal-service` 日志里说跳过了 Milvus bootstrap

这通常不是 Milvus 容器没起来，而是下面这个值没配置：

- `APP_RAG_QWEN_API_KEY`

不填也能启动，只是顾问服务会退回到本地知识检索。

### 6.4 AI 服务为什么没有连接 Python 推理服务

这是正常的，默认情况下：

- `APP_VISION_ENGINE=mock`

也就是说，组员第一次本地部署时，`ai-service` 默认先用 mock 识别引擎，目的是让项目更容易启动。

如果后面要接真实 Python 推理服务，再额外配置：

- `APP_VISION_ENGINE=python`
- `APP_VISION_PYTHON_BASE_URL=http://localhost:8091`

## 7. GitHub 协作规则

这一部分非常重要，请所有组员都遵守。

最核心的原则只有一句：

不要直接改 `main` 分支。

所有人都应该：

- 先从 `main` 拉最新代码
- 新建自己的功能分支
- 在自己的分支开发
- 自测没问题后发起合并请求
- 审核通过后再合并到 `main`

### 7.1 为什么不能直接改 `main`

如果每个人都直接往 `main` 提交，会出现这些问题：

- 互相覆盖代码
- 出错后很难定位是谁改坏了
- 一个人半成品代码会影响所有人

所以：

- `main` 只保留稳定代码
- 开发都在各自分支上完成

### 7.2 标准协作流程

每次开始开发前，都按这个顺序来：

#### 第一步：切到主分支并拉最新代码

```powershell
git checkout main
git pull origin main
```

#### 第二步：创建你自己的分支

命名建议：

- 新功能：`feature/功能名`
- 修 bug：`fix/问题名`
- 文档：`docs/文档名`

例如：

```powershell
git checkout -b feature/user-profile-page
```

#### 第三步：只在自己的分支上改代码

不要在 `main` 上直接改。

#### 第四步：改完后先自己测试

至少要做到：

- 项目能启动
- 你改的功能能正常跑
- 没有明显报错

#### 第五步：提交代码

```powershell
git add .
git commit -m "feat: add user profile page"
```

提交说明尽量简单清楚：

- `feat:` 新功能
- `fix:` 修复 bug
- `docs:` 文档修改
- `refactor:` 重构

#### 第六步：把你的分支推到 GitHub

```powershell
git push origin feature/user-profile-page
```

#### 第七步：在 GitHub 上发起 Pull Request

目标：

- 从你的分支
- 合并到 `main`

Pull Request 里请写清楚：

- 你改了什么
- 为什么改
- 怎么测试
- 有没有已知问题

### 7.3 合并前必须检查什么

发起合并前，请至少确认：

- 项目能启动
- 你改的功能正常
- 没把 `.env`、`.env.local` 之类敏感文件提交上去
- 没把真实密码、真实 API Key 写进代码
- 没有误删别人的代码

### 7.4 如果 `main` 更新了，你的分支怎么办

如果你开发到一半，别人已经把新代码合并进 `main`，你要先同步：

```powershell
git checkout main
git pull origin main
git checkout feature/user-profile-page
git merge main
```

如果出现冲突，不要慌：

- IDE 会标出冲突位置
- 看清楚两边代码的区别
- 不确定时先问组长或提 PR 讨论，不要乱删

### 7.5 哪些文件不要提交

下面这些不要提交：

- `.env`
- `.env.local`
- 个人 IDE 配置
- 本地日志
- 临时测试文件

仓库已经通过 `.gitignore` 忽略了一部分，但你提交前还是要自己看一眼：

```powershell
git status
```

### 7.6 适合 0 基础同学的最简单开发习惯

如果你现在还不熟 Git，就先记住这个最小流程：

1. `git checkout main`
2. `git pull origin main`
3. `git checkout -b feature/你的功能名`
4. 改代码
5. 测试
6. `git add .`
7. `git commit -m "feat: 你的修改说明"`
8. `git push origin 你的分支名`
9. 去 GitHub 发起合并请求

只要你始终不直接改 `main`，基本就不会出大问题。

## 8. 组长建议

如果你是项目维护者，建议你这样管理协作：

- 把 `main` 设为受保护分支
- 禁止直接 push 到 `main`
- 所有代码通过 Pull Request 合并
- 每个 PR 至少有 1 人看过再合并
- 大改动先在群里说清楚负责范围，避免多人改同一个文件

## 9. 一句话总结

这个项目的推荐开发方式是：

- 用 Docker 建基础设施
- 用脚本启动服务
- 用 `.env` 管理本地敏感配置
- 用分支开发
- 用 Pull Request 合并到 `main`

如果你严格照这个流程走，即使是第一次接触微服务项目，也能比较稳地参与协作。

## 10. LLaVA-NeXT 推理服务部署补充

如果你要把 `ai-service` 接到真实 Python 推理服务，而不是继续用 mock，请按下面做：

1. 进入 [ai-service/inference/python](/D:/安装网上应用集/Git/NutriMind_1/ai-service/inference/python)
2. 执行 `Copy-Item .env.example .env`
3. 安装依赖：`pip install -r requirements-llava.txt`
4. 启动推理服务：`.\start-llava-next.ps1 -Port 8091`
5. 在项目根目录 `.env` 里把 `APP_VISION_ENGINE` 改成 `python`
6. 同时确认 `APP_VISION_PYTHON_BASE_URL=http://localhost:8091`

补充说明：

- Python `/predict` 返回结构已经和 Java 现有 DTO 保持兼容
- `auto` 模式现在会优先走 `llava_next_retrieval`
- 如果 LLaVA 输出不稳定，Python 会自动回退到 `hybrid -> clip -> onnx -> manifest`
- 如果只是想继续跑轻量 ONNX 回退链路，可以改用 `.\start-food101-seed.ps1`
