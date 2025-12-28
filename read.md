你是资深 JavaFX 架构师与工程实现者。请用 Java 21 + Maven 生成一个可运行的 JavaFX 桌面应用（Windows/macOS/Linux），实现一个“CSV 表格监控器”App。必须使用 MVVM 架构，并使用 ControlsFX 的 FilteredTable 与 FilterTableColumn 作为核心表格组件。

目标功能（全部必须实现）：
1) CSV 导入与展示
- 从 resources 目录内置一个 sample.csv（你来生成，至少 200 行，包含：id(int)、symbol(String)、price(double)、qty(int)、status(String)、lastUpdate(ISO时间字符串) 这些列）
- 启动后自动加载 sample.csv 显示在表格中
- 提供“打开 CSV 文件”按钮：用户可选择本地 CSV 并加载替换数据
- CSV 解析需健壮：空值、非法数字要有默认策略并记录错误行（日志输出即可）

2) 实时更新（核心）
- 表格数据列表必须支持“实时更新”：后台线程/定时任务每 500ms 随机挑选若干行更新 price、qty、lastUpdate（模拟市场数据）
- 更新必须线程安全：后台线程更新模型，但 UI 更新必须正确切换到 JavaFX Application Thread（Platform.runLater 或更优雅方式）
- 需要可控：提供 Start / Pause 按钮控制实时更新
- 性能要求：2000 行以内更新流畅不卡顿（需要考虑批量更新/节流策略）

3) “其中一列有实时更新的功能”
- 重点列：price 列必须实时变化，并在 UI 中明显可见（例如闪烁/高亮一段时间）
- price 列同时支持用户手动编辑：双击单元格可编辑，提交后会覆盖实时更新的值（需要设计策略：例如对手动编辑过的行设置“锁定 5 秒不被自动更新”或提供“Unlock”按钮）

4) 过滤（ControlsFX）
- 使用 FilteredTable + FilterTableColumn 实现列级过滤
- 每列都能过滤：字符串列支持包含/前缀，数字列支持范围过滤（尽可能用 ControlsFX 的内置过滤能力）
- 过滤状态要保持：实时更新时不应破坏用户当前过滤结果（过滤后的视图应随更新动态变化）

5) Conditional Formatting（条件格式）
- 根据 status 或 price 变化设置行/单元格样式：
  - status == "ALERT" 时整行背景高亮
  - price 相比上一值上涨则 price 单元格显示“上涨样式”，下跌则显示“下跌样式”
- 不能用 setStyle 每次全表扫描造成卡顿：需要在 cellFactory 内按行数据变化做增量样式更新
- 样式写在单独的 CSS 文件中（resources/styles.css）

6) 查找（Search）
- 提供顶部搜索框：输入关键字可高亮匹配的行（匹配 symbol 或 status）
- 提供“跳转到下一个匹配”按钮（循环）
- 搜索高亮不应与过滤冲突：过滤缩小范围后，搜索仅在当前可见范围内工作

7) MVVM 结构（必须严格拆分）
- Model: RowModel（JavaFX Properties）、CsvRepository（读取CSV）、UpdateEngine（实时更新逻辑）
- ViewModel: TableViewModel（ObservableList、FilteredList/SortedList、命令：loadCsv/start/pause/search/nextMatch/unlockRow 等）
- View: JavaFX 界面（使用 FXML 或纯代码都可，但必须清晰分层；推荐 FXML + Controller 作为 View 层绑定 ViewModel）
- 不允许在 View 中写业务逻辑（例如 CSV 解析/定时更新/搜索算法都必须在 ViewModel/Model）

8) 工程交付要求
- 输出完整 Maven 工程结构（pom.xml、module-info.java 如果需要、src/main/java、src/main/resources）
- 依赖：OpenJFX + ControlsFX（版本选择与你代码匹配）
- 提供运行说明：mvn javafx:run 或 mvn clean package 后如何启动
- 给出关键类的完整代码（不要省略），确保可编译运行
- 为实时更新与编辑锁定策略写清晰注释

额外加分（尽量做）：
- 支持保存当前表格到 CSV（导出按钮）
- 用日志（java.util.logging 或 slf4j）记录解析与更新事件

输出格式要求：
- 先给出项目目录树
- 然后依次贴出每个文件的完整内容（代码块标明文件路径）
- 最后给出运行截图预期说明（文字描述即可）