package com.pluto.ndi.shared.excel;

/** 各工作簿导入入口共用的字节数和数据行上限。 */
public final class ExcelImportLimits {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public static final int MAX_ROWS = 2000;

    private ExcelImportLimits() {}

    public static void checkRowCount(int dataRows) {
        if (dataRows > MAX_ROWS) throw new RowLimitExceeded();
    }

    public static final class RowLimitExceeded extends IllegalArgumentException {
        private RowLimitExceeded() {
            super("一次最多导入 " + MAX_ROWS + " 行数据，表头不计入");
        }
    }
}
