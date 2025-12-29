package com.csvmonitor.view;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.control.SplitPane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight dock workspace that allows:
 * - Dragging tabs between dock areas
 * - Dropping tabs on edges to split the workspace (left/right/top/bottom)
 * - Grouping panels in TabPane-like groups (IDE style)
 *
 * This manager is intentionally self-contained (no external libs).
 */
public class DockManager {

    public record DockItem(String id, String title, Node content) { }

    private static final DataFormat DOCK_DATA_FORMAT = new DataFormat("dock-item-id");

    private final StackPane container;
    private final SplitPane rootSplit;
    private final Map<String, DockItem> items = new HashMap<>();

    private DockArea dragSourceArea;
    private DropMarker dropMarker;

    public DockManager(StackPane container) {
        this.container = container;
        this.rootSplit = new SplitPane();
        this.rootSplit.getStyleClass().add("dock-workspace");
        container.getChildren().add(rootSplit);
    }

    /**
     * Create a new dock area with initial items.
     */
    public DockArea createArea(String name, DockItem... initialItems) {
        DockArea area = new DockArea(this);
        area.setId("dock-area-" + name);
        addAreaToRoot(area);

        for (DockItem item : initialItems) {
            registerItem(item);
            area.addItem(item);
        }

        return area;
    }

    private void addAreaToRoot(DockArea area) {
        if (!rootSplit.getItems().contains(area)) {
            rootSplit.getItems().add(area);
            if (rootSplit.getItems().size() == 1) {
                rootSplit.setDividerPositions(0.65);
            }
        }
    }

    private void registerItem(DockItem item) {
        items.put(item.id(), item);
    }

    void handleDragDetected(DockArea sourceArea, DockItem item) {
        dragSourceArea = sourceArea;
        Dragboard db = sourceArea.startDrag();
        ClipboardContent content = new ClipboardContent();
        content.put(DOCK_DATA_FORMAT, item.id());
        db.setContent(content);
    }

    void handleDragOver(DockArea targetArea, DragEvent event) {
        String itemId = extractItemId(event.getDragboard());
        if (itemId == null) {
            return;
        }

        if (targetArea == dragSourceArea && targetArea.getTabs().size() == 1) {
            return; // nothing to do
        }

        DockDropPosition position = detectDropPosition(targetArea, event);
        event.acceptTransferModes(TransferMode.MOVE);
        showDropMarker(targetArea, position);
        event.consume();
    }

    void handleDragExited() {
        hideDropMarker();
    }

    void handleDrop(DockArea targetArea, DragEvent event) {
        String itemId = extractItemId(event.getDragboard());
        hideDropMarker();

        if (itemId == null) {
            event.setDropCompleted(false);
            return;
        }

        DockItem item = items.get(itemId);
        if (item == null) {
            event.setDropCompleted(false);
            return;
        }

        DockDropPosition position = detectDropPosition(targetArea, event);
        DockArea sourceArea = findAreaForItem(itemId);

        if (sourceArea != null) {
            sourceArea.removeItem(itemId);
        }

        if (position == DockDropPosition.CENTER) {
            targetArea.addItem(item);
            targetArea.selectItem(itemId);
        } else {
            DockArea newArea = new DockArea(this);
            newArea.addItem(item);
            splitArea(targetArea, newArea, position);
        }

        cleanupArea(sourceArea);
        event.setDropCompleted(true);
    }

    private DockArea findAreaForItem(String itemId) {
        for (DockArea area : collectAreas()) {
            if (area.containsItem(itemId)) {
                return area;
            }
        }
        return null;
    }

    private List<DockArea> collectAreas() {
        List<DockArea> areas = new ArrayList<>();
        collectAreasRecursive(rootSplit, areas);
        return areas;
    }

    private void collectAreasRecursive(Node node, List<DockArea> areas) {
        if (node instanceof DockArea area) {
            areas.add(area);
            return;
        }

        if (node instanceof SplitPane splitPane) {
            for (Node child : splitPane.getItems()) {
                collectAreasRecursive(child, areas);
            }
        }
    }

    private void cleanupArea(DockArea area) {
        if (area == null) {
            return;
        }

        if (!area.getTabs().isEmpty()) {
            return;
        }

        ParentInfo parentInfo = findParentInfo(area);
        if (parentInfo == null) {
            return;
        }

        SplitPane parent = parentInfo.parentSplit();
        parent.getItems().remove(area);

        // If split pane now has only one child, collapse it
        if (parent.getItems().size() == 1) {
            Node survivor = parent.getItems().get(0);
            ParentInfo grandParentInfo = findParentInfo(parent);
            if (grandParentInfo == null) {
                rootSplit.getItems().setAll(survivor);
                return;
            }
            replaceChild(grandParentInfo.parentSplit(), parent, survivor);
        }
    }

    private void splitArea(DockArea targetArea, DockArea newArea, DockDropPosition position) {
        Orientation orientation = (position == DockDropPosition.LEFT || position == DockDropPosition.RIGHT)
                ? Orientation.HORIZONTAL : Orientation.VERTICAL;

        ParentInfo parentInfo = findParentInfo(targetArea);

        if (parentInfo == null) {
            rootSplit.setOrientation(orientation);
            if (position == DockDropPosition.LEFT || position == DockDropPosition.TOP) {
                rootSplit.getItems().add(0, newArea);
                rootSplit.getItems().add(targetArea);
            } else {
                rootSplit.getItems().add(targetArea);
                rootSplit.getItems().add(newArea);
            }
            rootSplit.setDividerPositions(0.5);
            return;
        }

        SplitPane parent = parentInfo.parentSplit();
        if (parent.getOrientation() == orientation) {
            int idx = parent.getItems().indexOf(targetArea);
            if (position == DockDropPosition.LEFT || position == DockDropPosition.TOP) {
                parent.getItems().add(idx, newArea);
            } else {
                parent.getItems().add(idx + 1, newArea);
            }
            parent.setDividerPositions(0.5, 0.5);
        } else {
            SplitPane replacement = new SplitPane();
            replacement.setOrientation(orientation);
            if (position == DockDropPosition.LEFT || position == DockDropPosition.TOP) {
                replacement.getItems().addAll(newArea, targetArea);
            } else {
                replacement.getItems().addAll(targetArea, newArea);
            }
            replaceChild(parent, targetArea, replacement);
            replacement.setDividerPositions(0.5);
        }
    }

    private void replaceChild(SplitPane parent, Node target, Node replacement) {
        int idx = parent.getItems().indexOf(target);
        if (idx >= 0) {
            parent.getItems().set(idx, replacement);
        }
    }

    private DockDropPosition detectDropPosition(DockArea area, DragEvent event) {
        double x = event.getX();
        double y = event.getY();
        double width = area.getWidth();
        double height = area.getHeight();

        double leftZone = width * 0.25;
        double rightZone = width * 0.75;
        double topZone = height * 0.25;
        double bottomZone = height * 0.75;

        if (x < leftZone) return DockDropPosition.LEFT;
        if (x > rightZone) return DockDropPosition.RIGHT;
        if (y < topZone) return DockDropPosition.TOP;
        if (y > bottomZone) return DockDropPosition.BOTTOM;
        return DockDropPosition.CENTER;
    }

    private String extractItemId(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasContent(DOCK_DATA_FORMAT)) {
            return null;
        }
        Object value = dragboard.getContent(DOCK_DATA_FORMAT);
        return value != null ? value.toString() : null;
    }

    private void showDropMarker(DockArea area, DockDropPosition position) {
        if (dropMarker == null) {
            dropMarker = new DropMarker(container);
        }
        dropMarker.show(area, position);
    }

    private void hideDropMarker() {
        if (dropMarker != null) {
            dropMarker.hide();
        }
    }

    private ParentInfo findParentInfo(Node node) {
        Node parent = node.getParent();
        if (parent instanceof SplitPane splitPane) {
            return new ParentInfo(splitPane);
        }
        return null;
    }

    enum DockDropPosition {
        LEFT, RIGHT, TOP, BOTTOM, CENTER
    }

    /**
     * DockArea wraps a TabPane and exposes helpers for drag/drop.
     */
    static class DockArea extends TabPane {
        private final DockManager manager;

        DockArea(DockManager manager) {
            this.manager = manager;
            getStyleClass().add("dock-area");

            setOnDragOver(event -> manager.handleDragOver(this, event));
            setOnDragDropped(event -> manager.handleDrop(this, event));
            setOnDragExited(event -> manager.handleDragExited());
        }

        void addItem(DockItem item) {
            Tab tab = new Tab(item.title(), item.content());
            tab.setClosable(false);
            tab.setUserData(item.id());
            tab.setGraphic(createTabHeader(item));
            getTabs().add(tab);
        }

        void selectItem(String itemId) {
            for (Tab tab : getTabs()) {
                if (Objects.equals(tab.getUserData(), itemId)) {
                    getSelectionModel().select(tab);
                    return;
                }
            }
        }

        boolean containsItem(String itemId) {
            return getTabs().stream().anyMatch(t -> Objects.equals(t.getUserData(), itemId));
        }

        void removeItem(String itemId) {
            getTabs().removeIf(t -> Objects.equals(t.getUserData(), itemId));
        }

        private Node createTabHeader(DockItem item) {
            Label label = new Label(item.title());
            label.getStyleClass().add("dock-tab-title");
            label.setFont(Font.font(null, FontWeight.SEMI_BOLD, 12));

            VBox header = new VBox(label);
            header.getStyleClass().add("dock-tab-header");
            header.setOnDragDetected(e -> manager.handleDragDetected(this, item));
            return header;
        }

        Dragboard startDrag() {
            return startDragAndDrop(TransferMode.MOVE);
        }
    }

    /**
     * Visual marker showing where the dock will land.
     */
    static class DropMarker {
        private final StackPane overlayRoot;
        private final Rectangle marker;

        DropMarker(StackPane container) {
            this.overlayRoot = container;
            this.marker = new Rectangle();
            marker.setManaged(false);
            marker.setFill(Color.rgb(64, 128, 255, 0.15));
            marker.setStroke(Color.rgb(64, 128, 255));
            marker.setStrokeWidth(2);
            marker.setStrokeType(StrokeType.INSIDE);
            marker.getStyleClass().add("dock-drop-marker");
        }

        void show(DockArea area, DockDropPosition position) {
            if (!overlayRoot.getChildren().contains(marker)) {
                overlayRoot.getChildren().add(marker);
            }

            double width = area.getWidth();
            double height = area.getHeight();
            double localX = area.localToScene(area.getBoundsInLocal()).getMinX()
                    - overlayRoot.localToScene(overlayRoot.getBoundsInLocal()).getMinX();
            double localY = area.localToScene(area.getBoundsInLocal()).getMinY()
                    - overlayRoot.localToScene(overlayRoot.getBoundsInLocal()).getMinY();

            switch (position) {
                case LEFT -> {
                    marker.setX(localX);
                    marker.setY(localY);
                    marker.setWidth(width * 0.35);
                    marker.setHeight(height);
                }
                case RIGHT -> {
                    marker.setX(localX + width * 0.65);
                    marker.setY(localY);
                    marker.setWidth(width * 0.35);
                    marker.setHeight(height);
                }
                case TOP -> {
                    marker.setX(localX);
                    marker.setY(localY);
                    marker.setWidth(width);
                    marker.setHeight(height * 0.35);
                }
                case BOTTOM -> {
                    marker.setX(localX);
                    marker.setY(localY + height * 0.65);
                    marker.setWidth(width);
                    marker.setHeight(height * 0.35);
                }
                case CENTER -> {
                    marker.setX(localX + width * 0.15);
                    marker.setY(localY + height * 0.15);
                    marker.setWidth(width * 0.7);
                    marker.setHeight(height * 0.7);
                }
            }
        }

        void hide() {
            overlayRoot.getChildren().remove(marker);
        }
    }

    /**
     * Holds parent split info for replacement.
     */
    record ParentInfo(SplitPane parentSplit) { }
}
