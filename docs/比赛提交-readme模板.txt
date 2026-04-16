NutriMind 作品安装与部署说明

一、作品基本信息
1. 作品名称：知食分子 / NutriMind
2. 作品类型：移动应用开发类
3. 运行方式：Android 安装包 + 后端服务 + Python 图像识别推理服务

二、压缩包说明
本压缩包主要包含以下内容：
1. 源码目录：包含移动端、后台管理端、后端微服务、AI 推理对接代码、公共模块、部署脚本等团队开发源码。
2. 代表性素材目录：包含作品 Logo、关键页面截图、图像识别测试图等代表性素材。
3. 安装部署说明：说明系统运行环境、部署步骤和打包方式。

三、运行环境要求
1. 操作系统：Windows 10/11 或 Linux 服务器环境
2. Java：JDK 17
3. Maven：3.9 及以上
4. Node.js：建议 18 及以上
5. HBuilderX：用于 uni-app Android 安装包打包
6. Python：建议 3.10
7. Docker Desktop / Docker Engine
8. MySQL：通过 Docker 部署
9. Redis、Nacos、Milvus：通过 Docker 部署

四、项目源码结构说明
1. frontend-app：uni-app 移动端源码
2. admin-web：Vue3 后台管理系统源码
3. gateway-service：网关服务
4. user-service：用户服务
5. food-service：食物库服务
6. meal-service：饮食记录、计划、社区与顾问服务
7. ai-service：AI 服务接口层
8. common-core：公共模块
9. scripts：项目启动脚本
10. docker：容器初始化脚本

五、环境变量配置说明
1. 请将 `.env.example` 复制为 `.env`
2. 根据本机环境填写以下参数：
   - APP_JWT_SECRET
   - MYSQL_PASSWORD
   - MYSQL_ROOT_PASSWORD
   - MYSQL_HOST
   - MYSQL_PORT
   - MYSQL_DB
   - MYSQL_FOOD_DB
   - MYSQL_MEAL_DB
   - NACOS_SERVER_ADDR
   - APP_VISION_ENGINE
   - APP_VISION_PYTHON_BASE_URL
3. 若仅用于本地开发，可使用 Docker MySQL，默认端口为 3307

六、后端服务部署步骤
1. 启动基础设施：
   docker compose -f docker-compose.dev.yml up -d

2. 启动微服务：
   powershell -ExecutionPolicy Bypass -File .\scripts\start-local-discovery.ps1

3. 默认服务端口如下：
   - gateway-service：8080
   - user-service：8081
   - food-service：8082
   - meal-service：8083
   - ai-service：8084
   - Python 图像识别推理服务：8091
   - Nacos：8848

七、Python 图像识别服务说明
1. 图像识别功能由 Java `ai-service` 和 Python 推理服务联合完成。
2. Python 服务源码位于：
   `ai-service/inference/python/`
3. 依赖安装可参考：
   - requirements.txt
   - requirements-ai4food.txt
   - README-ai4food.md
4. 启动方式示例：
   powershell -ExecutionPolicy Bypass -File .\ai-service\inference\python\start-ai4food-subcategory.ps1

八、模型文件说明
1. 本项目图像识别模块依赖模型权重文件。
2. 由于模型权重文件体积较大，不建议随比赛源码包一并提交。
3. 若需完整复现，请将模型文件放置到以下目录：
   `model/ai4food_subcategory/`
4. 目录下还需保留以下配置文件：
   - metadata.json
   - subcategory_classes.txt

九、移动端安装包生成方式
1. 使用 HBuilderX 打开 `frontend-app`
2. 在 `manifest.json` 中配置应用信息、AppID、版本号和图标
3. 确保前端接口地址已指向可访问的网关地址
4. 执行：
   发行 -> 原生App-云打包 -> Android
5. 生成最终 APK 文件

十、评审使用说明
1. 本作品移动端安装包依赖后端服务支持。
2. 若评委需要完整体验登录、食物识别、营养问答、饮食计划和社区功能，需保证后端服务已部署并可访问。
3. 推荐将后端部署到云服务器或公网可访问环境，并将移动端请求地址配置为对应服务器地址。

十一、建议提供的测试信息
1. 测试账号：________________
2. 测试密码：________________
3. 后端访问地址：________________
4. 备注说明：如图像识别服务或大模型需单独部署，请在此补充说明。

十二、提交建议
1. 安装包建议命名为：
   `作品编号-安装包.apk`
2. 源码与素材压缩包建议命名为：
   `作品编号-素材源码.zip`
3. 若素材文件超过 10 个，建议统一压缩后提交。

十三、补充说明
本作品包含移动端应用、后台管理系统、后端微服务、RAG 检索增强问答模块、Agent 个性化计划生成模块以及图像识别模块。比赛提交时建议优先保证以下材料完整：
1. 最终 APK
2. 可运行源码
3. 安装部署说明
4. 代表性素材
5. 测试账号与后端访问信息
