package com.natixis.etrading.gui.viewmodel;

import javafx.beans.value.ObservableValue;
import java.util.function.Function;

public record ColumnConfig<T>(
        String id,
        String title,
        double prefWidth,
        double minWidth,
        String alignment,
        Class<T> valueType,
        Function<RowViewModel, ObservableValue<T>> propertyGetter,
        Function<RowViewModel, String> styleClassGetter
) {
    /**
     * Alignment constants.
     */
    public static final String ALIGN_LEFT = "-fx-alignment: CENTER-LEFT;";
    public static final String ALIGN_CENTER = "-fx-alignment: CENTER;";
    public static final String ALIGN_RIGHT = "-fx-alignment: CENTER-RIGHT;";

    /**
     * Create a string column config.
     */
    public static ColumnConfig<String> stringColumn(
            String id,
            String title,
            double prefWidth,
            double minWidth,
            String alignment,
            Function<RowViewModel, ObservableValue<String>> propertyGetter
    ) {
        return new ColumnConfig<>(id, title, prefWidth, minWidth, alignment,
                String.class, propertyGetter, null);
    }

    /**
     * Create a string column with style class.
     */
    public static ColumnConfig<String> stringColumnWithStyle(
            String id,
            String title,
            double prefWidth,
            double minWidth,
            String alignment,
            Function<RowViewModel, ObservableValue<String>> propertyGetter,
            Function<RowViewModel, String> styleClassGetter
    ) {
        return new ColumnConfig<>(id, title, prefWidth, minWidth, alignment,
                String.class, propertyGetter, styleClassGetter);
    }

    /**
     * Create a number column config.
     */
    @SuppressWarnings("unchecked")
    public static ColumnConfig<Number> numberColumn(
            String id,
            String title,
            double prefWidth,
            double minWidth,
            String alignment,
            Function<RowViewModel, ? extends ObservableValue<? extends Number>> propertyGetter
    ) {
        return new ColumnConfig<>(id, title, prefWidth, minWidth, alignment,
                Number.class, 
                (Function<RowViewModel, ObservableValue<Number>>) (Function<?, ?>) propertyGetter, 
                null);
    }

    /**
     * Create a number column with style class.
     */
    @SuppressWarnings("unchecked")
    public static ColumnConfig<Number> numberColumnWithStyle(
            String id,
            String title,
            double prefWidth,
            double minWidth,
            String alignment,
            Function<RowViewModel, ? extends ObservableValue<? extends Number>> propertyGetter,
            Function<RowViewModel, String> styleClassGetter
    ) {
        return new ColumnConfig<>(id, title, prefWidth, minWidth, alignment,
                Number.class, 
                (Function<RowViewModel, ObservableValue<Number>>) (Function<?, ?>) propertyGetter, 
                styleClassGetter);
    }

    /**
     * Check if this column has custom styling.
     */
    public boolean hasCustomStyle() {
        return styleClassGetter != null;
    }

    /**
     * Check if this is a number column.
     */
    public boolean isNumberColumn() {
        return Number.class.isAssignableFrom(valueType);
    }
}
