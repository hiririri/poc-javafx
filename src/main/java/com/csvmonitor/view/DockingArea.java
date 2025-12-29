package com.csvmonitor.view;

import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

class DockingArea extends StackPane {
    private static final String DRAG_KEY = "dock-tab";
    private static final String TAB_ID_KEY = "dock.id";
    private static final double EDGE_ZONE = 24.0;

    private final DragContext dragContext = new DragContext();
    private final Pane overlayPane;
    private final Pane dropPreview;
    private DockDropRegion previewRegion;
    private Node rootNode;
    private final BooleanProperty splitActive = new SimpleBooleanProperty(false);
    private final Map<String, Tab> tabRegistry = new LinkedHashMap<>();

    DockingArea() {
        overlayPane = new Pane();
        overlayPane.setMouseTransparent(true);
        overlayPane.setPickOnBounds(false);

        dropPreview = new Pane();
        dropPreview.setManaged(false);
        dropPreview.setVisible(false);
        dropPreview.getStyleClass().add("dock-preview");
        overlayPane.getChildren().add(dropPreview);

        DockTabPane rootPane = new DockTabPane(this);
        setRoot(rootPane);
    }

    void addTab(String id, Tab tab) {
        DockTabPane rootPane;
        if (rootNode instanceof DockTabPane pane) {
            rootPane = pane;
        } else {
            rootPane = new DockTabPane(this);
            setRoot(rootPane);
        }
        tab.getProperties().put(TAB_ID_KEY, id);
        tabRegistry.put(id, tab);
        rootPane.addDockTab(tab);
    }

    void startDrag(DockTabPane source, Tab tab, MouseEvent event) {
        Dragboard dragboard = ((Node) event.getSource()).startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString(DRAG_KEY);
        dragboard.setContent(content);
        dragContext.source = source;
        dragContext.tab = tab;
        event.consume();
    }

    void clearDrag() {
        dragContext.source = null;
        dragContext.tab = null;
        hidePreview();
    }

    String saveLayout() {
        if (rootNode == null) {
            return "";
        }
        return new LayoutCodec().encode(rootNode);
    }

    boolean restoreLayout(String layout) {
        if (layout == null || layout.isBlank()) {
            return false;
        }
        LayoutCodec codec = new LayoutCodec();
        Node node = codec.decode(layout, this);
        if (node == null) {
            return false;
        }
        setRoot(node);
        if (collectTabs().isEmpty()) {
            resetLayout();
            return false;
        }
        return true;
    }

    void resetLayout() {
        List<Tab> tabs = List.copyOf(tabRegistry.values());
        DockTabPane pane = new DockTabPane(this);
        for (Tab tab : tabs) {
            pane.addDockTab(tab);
        }
        setRoot(pane);
    }

    void handleDrop(DockTabPane target, double x, double y) {
        hidePreview();
        Tab tab = dragContext.tab;
        DockTabPane source = dragContext.source;
        if (tab == null || source == null) {
            return;
        }

        DockDropRegion region = resolveRegion(target, x, y);
        if (region == DockDropRegion.CENTER) {
            if (source != target) {
                moveTab(tab, source, target);
            }
            return;
        }

        splitAndDock(tab, source, target, region);
    }

    private DockDropRegion resolveRegion(DockTabPane target, double x, double y) {
        double width = target.getWidth();
        double height = target.getHeight();
        if (x < EDGE_ZONE) {
            return DockDropRegion.LEFT;
        }
        if (x > width - EDGE_ZONE) {
            return DockDropRegion.RIGHT;
        }
        if (y < EDGE_ZONE) {
            return DockDropRegion.TOP;
        }
        if (y > height - EDGE_ZONE) {
            return DockDropRegion.BOTTOM;
        }
        return DockDropRegion.CENTER;
    }

    private void moveTab(Tab tab, DockTabPane source, DockTabPane target) {
        source.getTabs().remove(tab);
        target.addDockTab(tab);
        cleanupIfEmpty(source);
    }

    private void splitAndDock(Tab tab, DockTabPane source, DockTabPane target, DockDropRegion region) {
        Orientation orientation = (region == DockDropRegion.LEFT || region == DockDropRegion.RIGHT)
                ? Orientation.HORIZONTAL
                : Orientation.VERTICAL;

        ParentInfo parentInfo = removeFromParent(target);

        DockTabPane newPane = new DockTabPane(this);
        source.getTabs().remove(tab);
        newPane.addDockTab(tab);

        SplitPane split = new SplitPane();
        split.setOrientation(orientation);
        if (region == DockDropRegion.LEFT || region == DockDropRegion.TOP) {
            split.getItems().addAll(newPane, target);
        } else {
            split.getItems().addAll(target, newPane);
        }
        split.setDividerPositions(0.5);

        insertIntoParent(parentInfo, split);
        if (source != target) {
            cleanupIfEmpty(source);
        }
    }

    private void cleanupIfEmpty(DockTabPane pane) {
        if (!pane.getTabs().isEmpty()) {
            return;
        }
        ParentInfo parentInfo = removeFromParent(pane);
        if (parentInfo.parent instanceof SplitPane split && split.getItems().size() == 1) {
            Node remaining = split.getItems().get(0);
            ParentInfo splitParentInfo = removeFromParent(split);
            insertIntoParent(splitParentInfo, remaining);
        }
    }

    private void collapseEmptyPane(DockTabPane pane) {
        cleanupIfEmpty(pane);
    }

    private ParentInfo removeFromParent(Node node) {
        Parent parent = node.getParent();
        int index = -1;
        if (parent instanceof SplitPane split) {
            index = split.getItems().indexOf(node);
            split.getItems().remove(node);
        } else if (parent instanceof StackPane stack) {
            index = stack.getChildren().indexOf(node);
            stack.getChildren().remove(node);
        }
        return new ParentInfo(parent, index);
    }

    private void insertIntoParent(ParentInfo info, Node node) {
        if (info.parent instanceof SplitPane split) {
            int index = info.index >= 0 ? info.index : split.getItems().size();
            split.getItems().add(index, node);
            updateSplitState();
            return;
        }
        if (info.parent instanceof StackPane stack) {
            stack.getChildren().setAll(node, overlayPane);
            if (stack == this) {
                rootNode = node;
            }
            updateSplitState();
            return;
        }
        setRoot(node);
    }

    private void setRoot(Node node) {
        getChildren().setAll(node, overlayPane);
        rootNode = node;
        updateSplitState();
    }

    private void updatePreview(DockTabPane target, double x, double y) {
        DockDropRegion region = resolveRegion(target, x, y);
        if (previewRegion != region) {
            dropPreview.getStyleClass().removeAll(
                    "dock-preview-left",
                    "dock-preview-right",
                    "dock-preview-top",
                    "dock-preview-bottom",
                    "dock-preview-center");
            dropPreview.getStyleClass().add("dock-preview-" + region.name().toLowerCase());
            previewRegion = region;
        }

        Bounds targetBounds = target.localToScene(target.getLayoutBounds());
        Bounds localBounds = sceneToLocal(targetBounds);
        double insetX = localBounds.getMinX();
        double insetY = localBounds.getMinY();
        double width = localBounds.getWidth();
        double height = localBounds.getHeight();

        double splitRatio = 0.35;
        double previewX = insetX;
        double previewY = insetY;
        double previewW = width;
        double previewH = height;

        switch (region) {
            case LEFT -> previewW = width * splitRatio;
            case RIGHT -> {
                previewW = width * splitRatio;
                previewX = insetX + width - previewW;
            }
            case TOP -> previewH = height * splitRatio;
            case BOTTOM -> {
                previewH = height * splitRatio;
                previewY = insetY + height - previewH;
            }
            case CENTER -> {
                // full highlight
            }
        }

        dropPreview.resizeRelocate(previewX, previewY, previewW, previewH);
        dropPreview.setVisible(true);
    }

    private void hidePreview() {
        dropPreview.setVisible(false);
        previewRegion = null;
    }

    private enum DockDropRegion {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        CENTER
    }

    private static class DragContext {
        private DockTabPane source;
        private Tab tab;
    }

    private record ParentInfo(Parent parent, int index) {}

    static class DockTabPane extends TabPane {
        private final DockingArea dockingArea;

        DockTabPane(DockingArea dockingArea) {
            this.dockingArea = dockingArea;
            setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            setOnDragOver(event -> {
                Dragboard dragboard = event.getDragboard();
                if (dragboard.hasString() && DRAG_KEY.equals(dragboard.getString())) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    dockingArea.updatePreview(this, event.getX(), event.getY());
                }
                event.consume();
            });
            setOnDragExited(event -> {
                dockingArea.hidePreview();
                event.consume();
            });
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && getTabs().isEmpty()) {
                    dockingArea.collapseEmptyPane(this);
                    event.consume();
                }
            });
            setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                if (dragboard.hasString() && DRAG_KEY.equals(dragboard.getString())) {
                    dockingArea.handleDrop(this, event.getX(), event.getY());
                    event.setDropCompleted(true);
                } else {
                    event.setDropCompleted(false);
                }
                dockingArea.clearDrag();
                event.consume();
            });
        }

        void addDockTab(Tab tab) {
            makeDockable(tab);
            getTabs().add(tab);
            getSelectionModel().select(tab);
        }

        private void makeDockable(Tab tab) {
            Label grip = new Label("::");
            grip.getStyleClass().add("dock-grip");
            grip.setOnDragDetected(event -> dockingArea.startDrag(this, tab, event));
            grip.setOnDragDone(event -> dockingArea.clearDrag());
            tab.setGraphic(grip);
            tab.setClosable(false);
        }
    }

    private static final class LayoutCodec {
        String encode(Node node) {
            if (node instanceof DockTabPane pane) {
                StringBuilder builder = new StringBuilder();
                builder.append("T[");
                boolean first = true;
                for (Tab tab : pane.getTabs()) {
                    Object id = tab.getProperties().get(TAB_ID_KEY);
                    if (!(id instanceof String)) {
                        continue;
                    }
                    if (!first) {
                        builder.append(",");
                    }
                    builder.append(id);
                    first = false;
                }
                builder.append("]");
                return builder.toString();
            }
            if (node instanceof SplitPane split) {
                StringBuilder builder = new StringBuilder();
                builder.append("S[");
                builder.append(split.getOrientation() == Orientation.HORIZONTAL ? "H" : "V");
                builder.append(";");
                double[] dividers = split.getDividerPositions();
                for (int i = 0; i < dividers.length; i++) {
                    if (i > 0) {
                        builder.append(",");
                    }
                    builder.append(String.format(java.util.Locale.ROOT, "%.4f", dividers[i]));
                }
                for (Node child : split.getItems()) {
                    builder.append(";");
                    builder.append(encode(child));
                }
                builder.append("]");
                return builder.toString();
            }
            return "";
        }

        Node decode(String data, DockingArea dockingArea) {
            LayoutReader reader = new LayoutReader(data);
            try {
                return reader.parseNode(dockingArea);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private static final class LayoutReader {
        private final String data;
        private int index;

        private LayoutReader(String data) {
            this.data = data.trim();
        }

        Node parseNode(DockingArea dockingArea) {
            if (peek() == 'T') {
                return parseTabPane(dockingArea);
            }
            if (peek() == 'S') {
                return parseSplitPane(dockingArea);
            }
            throw new IllegalArgumentException("Unknown layout token");
        }

        private DockTabPane parseTabPane(DockingArea dockingArea) {
            expect('T');
            expect('[');
            DockTabPane pane = new DockTabPane(dockingArea);
            String ids = readUntil(']');
            if (!ids.isBlank()) {
                String[] parts = ids.split(",");
                for (String part : parts) {
                    String id = part.trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    Tab tab = dockingArea.findTabById(id);
                    if (tab != null) {
                        if (tab.getTabPane() != null) {
                            tab.getTabPane().getTabs().remove(tab);
                        }
                        pane.addDockTab(tab);
                    }
                }
            }
            expect(']');
            return pane;
        }

        private SplitPane parseSplitPane(DockingArea dockingArea) {
            expect('S');
            expect('[');
            char orientationToken = readChar();
            Orientation orientation = orientationToken == 'H'
                    ? Orientation.HORIZONTAL
                    : Orientation.VERTICAL;
            expect(';');
            String dividerChunk = readUntil(';');
            expect(';');
            SplitPane split = new SplitPane();
            split.setOrientation(orientation);
            while (peek() != ']') {
                Node child = parseNode(dockingArea);
                split.getItems().add(child);
                if (peek() == ';') {
                    readChar();
                }
            }
            expect(']');
            if (!dividerChunk.isBlank()) {
                String[] parts = dividerChunk.split(",");
                double[] dividers = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    dividers[i] = Double.parseDouble(parts[i]);
                }
                split.setDividerPositions(dividers);
            }
            return split;
        }

        private char peek() {
            if (index >= data.length()) {
                return '\0';
            }
            return data.charAt(index);
        }

        private char readChar() {
            if (index >= data.length()) {
                throw new IllegalArgumentException("Unexpected end of layout");
            }
            return data.charAt(index++);
        }

        private void expect(char expected) {
            char value = readChar();
            if (value != expected) {
                throw new IllegalArgumentException("Expected " + expected + " but got " + value);
            }
        }

        private String readUntil(char terminator) {
            int start = index;
            while (index < data.length() && data.charAt(index) != terminator) {
                index++;
            }
            if (index >= data.length()) {
                throw new IllegalArgumentException("Missing terminator " + terminator);
            }
            return data.substring(start, index);
        }
    }

    private Tab findTabById(String id) {
        return tabRegistry.get(id);
    }

    private List<Tab> collectTabs() {
        List<Tab> tabs = new java.util.ArrayList<>();
        collectTabs(rootNode, tabs);
        return tabs;
    }

    private void collectTabs(Node node, List<Tab> tabs) {
        if (node instanceof DockTabPane pane) {
            tabs.addAll(pane.getTabs());
            return;
        }
        if (node instanceof SplitPane split) {
            for (Node child : split.getItems()) {
                collectTabs(child, tabs);
            }
        }
    }

    boolean isSplitActive() {
        return splitActive.get();
    }

    ReadOnlyBooleanProperty splitActiveProperty() {
        return splitActive;
    }

    private void updateSplitState() {
        splitActive.set(rootNode instanceof SplitPane);
    }
}
