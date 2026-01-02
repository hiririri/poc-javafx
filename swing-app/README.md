# CSV Table Monitor - Swing Version

这是 CSV Table Monitor 的 Swing 版本，用于与 JavaFX 版本进行对比。

## 功能对比

| 功能 | JavaFX 版本 | Swing 版本 |
|------|-------------|------------|
| 表格组件 | FilteredTableView (ControlsFX) | JTable + TableRowSorter |
| 数据绑定 | JavaFX Properties (自动绑定) | PropertyChangeSupport (手动通知) |
| 线程模型 | Platform.runLater() | SwingUtilities.invokeLater() |
| 样式 | CSS 样式表 | 自定义 CellRenderer |
| 过滤 | 内置 SouthFilter | 自定义 FilterPanel + RowFilter |
| 排序 | 内置支持 | TableRowSorter |
| 外观 | JavaFX 默认主题 | FlatLaf 现代主题 |

## 架构对比

### JavaFX 版本 (MVVM)
```
View (MainController) 
    ↓ 数据绑定
ViewModel (TableViewModel, RowViewModel)
    ↓
Model (RowModel, CsvRepository, UpdateEngine)
```

### Swing 版本 (MVC)
```
View (MainFrame, CsvTableModel, Renderers)
    ↓ 事件监听
Model (RowData, CsvRepository, UpdateEngine)
```

## 运行方式

### 运行 Swing 版本
```bash
cd swing-app
mvn compile exec:java
```

### 运行 JavaFX 版本
```bash
# 在项目根目录
mvn javafx:run
```

## 代码量对比

| 组件 | JavaFX | Swing |
|------|--------|-------|
| Model层 | RowModel (143行) | RowData (143行) |
| 数据绑定 | RowViewModel (227行) | - |
| 主控制器 | MainController (554行) | MainFrame (320行) |
| 表格模型 | - | CsvTableModel (110行) |
| 单元格渲染 | 内置+自定义 (170行) | StatusCellRenderer (130行) |
| 过滤面板 | 内置 | FilterPanel (130行) |

## 主要差异

### 1. 数据绑定
- **JavaFX**: 使用 `ObservableValue` 和 `Property` 实现自动双向绑定
- **Swing**: 需要手动调用 `fireTableDataChanged()` 触发更新

### 2. 样式系统
- **JavaFX**: CSS 样式表，支持伪类和动画
- **Swing**: 通过 `CellRenderer` 在 Java 代码中设置样式

### 3. 过滤功能
- **JavaFX**: ControlsFX 的 `FilteredTableView` 提供内置过滤 UI
- **Swing**: 需要自定义 `FilterPanel` 和 `RowFilter`

### 4. 线程安全
- **JavaFX**: `Platform.runLater()` 
- **Swing**: `SwingUtilities.invokeLater()`

### 5. 现代外观
- **JavaFX**: 原生支持现代 UI
- **Swing**: 需要第三方库 (FlatLaf) 获得现代外观

## 性能考虑

两个版本都支持：
- 异步加载大数据集
- 批量更新优化
- Virtual Threads (Java 21)

## 依赖

### Swing 版本
- FlatLaf 3.4 (现代外观)
- SLF4J 2.0.9 (日志)

### JavaFX 版本
- JavaFX 21.0.2
- ControlsFX 11.2.1
- SLF4J 2.0.9

