# CSV Table Monitor

一个基于 JavaFX 的实时 CSV 表格监控应用，支持实时数据更新、过滤、搜索和条件格式化。

## 技术栈

- **Java 21**
- **JavaFX 21.0.2**
- **ControlsFX 11.2.1** - 提供 FilteredTableView、FilteredTableColumn、SouthFilter 过滤组件
- **SLF4J** - 日志记录
- **Maven** - 项目构建

## 项目结构

```
poc-javafx/
├── pom.xml                                    # Maven 配置
├── README.md                                  # 项目说明
└── src/main/
    ├── java/
    │   ├── module-info.java                   # Java 模块定义
    │   └── com/csvmonitor/
    │       ├── model/                         # Model 层
    │       │   ├── RowModel.java              # 行数据模型 (JavaFX Properties)
    │       │   ├── CsvRepository.java         # CSV 读写操作
    │       │   └── UpdateEngine.java          # 实时更新引擎
    │       ├── viewmodel/                     # ViewModel 层
    │       │   └── TableViewModel.java        # 表格视图模型
    │       └── view/                          # View 层
    │           ├── App.java                   # 应用入口
    │           └── MainController.java        # 主界面控制器
    └── resources/
        ├── MainView.fxml                      # FXML 界面定义
        ├── styles.css                         # CSS 样式
        └── sample.csv                         # 示例数据 (220 行)
```

## MVVM 架构说明

### Model 层
- **RowModel**: 使用 JavaFX Properties 实现数据绑定，支持 id、symbol、price、qty、status、lastUpdate 字段
- **CsvRepository**: 负责 CSV 文件的读取和保存，支持健壮的错误处理
- **UpdateEngine**: 后台定时任务引擎，每 500ms 随机更新数据，线程安全

### ViewModel 层
- **TableViewModel**: 管理 ObservableList、FilteredList、SortedList
- 提供命令: loadCsv、startUpdates、pauseUpdates、search、nextMatch、unlockRow
- 协调 Model 层组件

### View 层
- **App.java**: 应用程序入口，加载 FXML 和 CSS
- **MainController.java**: 
  - 使用 **FilteredTableView** 和 **FilteredTableColumn** (ControlsFX)
  - 使用 **SouthFilter** 为每列提供过滤功能
  - 绑定 UI 组件到 ViewModel，处理用户事件
- **MainView.fxml**: 界面布局定义（表格在代码中动态创建）

## 运行方式

### 方式 1: 使用 Maven 插件直接运行

```bash
cd /home/qjiang/poc-javafx
mvn clean javafx:run
```

### 方式 2: 打包后运行

```bash
cd /home/qjiang/poc-javafx
mvn clean package
java --module-path target/classes:$HOME/.m2/repository/org/openjfx/javafx-base/21.0.2/javafx-base-21.0.2-linux.jar:$HOME/.m2/repository/org/openjfx/javafx-controls/21.0.2/javafx-controls-21.0.2-linux.jar:$HOME/.m2/repository/org/openjfx/javafx-fxml/21.0.2/javafx-fxml-21.0.2-linux.jar:$HOME/.m2/repository/org/openjfx/javafx-graphics/21.0.2/javafx-graphics-21.0.2-linux.jar:$HOME/.m2/repository/org/controlsfx/controlsfx/11.2.1/controlsfx-11.2.1.jar --add-modules com.csvmonitor -m com.csvmonitor/com.csvmonitor.view.App
```

推荐使用方式 1，更简单。

## 功能说明

### 1. CSV 导入与展示
- 启动时自动加载内置的 `sample.csv`（220 行示例数据）
- 点击 **📁 Open CSV** 按钮可选择本地 CSV 文件
- CSV 解析支持空值和非法数字的默认处理，错误记录到日志

### 2. 实时更新
- 点击 **▶ Start** 启动实时更新（每 500ms 更新若干行）
- 点击 **⏸ Pause** 暂停更新
- 更新的字段：price、qty、status、lastUpdate
- 线程安全：后台线程更新数据，UI 线程刷新界面

### 3. Price 列特殊功能
- **实时变化高亮**：价格上涨显示绿色，下跌显示红色
- **手动编辑**：双击 Price 单元格可编辑
- **编辑锁定**：手动编辑后，该行 5 秒内不会被自动更新
- **Actions 列**：锁定的行会显示 🔓 按钮，点击可立即解锁
- **全部解锁**：点击 **🔓 Unlock All** 解锁所有行

### 4. 过滤功能 (ControlsFX FilteredTableView + FilteredTableColumn)
- 使用 **FilteredTableView** 和 **FilteredTableColumn** 作为核心表格组件
- 每列下方有 **SouthFilter** 过滤输入框
- 支持字符串包含匹配和数字范围过滤
- 支持多列同时过滤
- 过滤状态在实时更新时保持

### 5. 条件格式化
- **ALERT 状态**：整行背景变为深红色
- **价格上涨**：Price 单元格显示绿色
- **价格下跌**：Price 单元格显示红色
- **锁定行**：左边框显示橙色指示条

### 6. 搜索功能
- 在搜索框输入关键字，匹配 symbol 或 status 列
- 匹配的行高亮显示（黄绿色背景）
- 点击 **Next ▼** 跳转到下一个匹配项（循环）
- 右侧显示当前匹配位置（如 1/5）

### 7. 导出 CSV
- 点击 **💾 Save CSV** 保存当前表格数据到文件

## 界面预期说明

启动后将看到：

1. **顶部工具栏**：
   - 应用标题 "📊 CSV Table Monitor"
   - 行数显示
   - 文件操作按钮（Open CSV, Save CSV）
   - 更新控制按钮（Start/Pause, Unlock All）
   - 搜索区域（搜索框、Next 按钮、匹配计数）

2. **中央表格** (FilteredTableView)：
   - 7 列：ID, Symbol, Price, Qty, Status, Last Update, Actions
   - 清爽浅色主题，表头蓝色
   - **列头下方有过滤输入行 (SouthFilter)**
   - 交替行颜色（白色/浅灰）
   - 列头可点击排序

3. **底部状态栏**：
   - 左侧显示操作状态（如 "Loaded 220 rows from sample.csv"）
   - 右侧显示操作提示

4. **实时效果**（点击 Start 后）：
   - Price 列数值随机变化
   - 上涨绿色、下跌红色
   - 偶尔有行变成 ALERT 状态（深红背景）

## 注意事项

1. 需要 Java 21 或更高版本
2. 首次运行需要下载 Maven 依赖
3. 在 WSL 环境中运行需要配置 X11 显示（如使用 VcXsrv 或 WSLg）

## 日志配置

应用使用 SLF4J + slf4j-simple 记录日志，默认输出到控制台。
可在运行时通过系统属性调整日志级别：

```bash
mvn javafx:run -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

