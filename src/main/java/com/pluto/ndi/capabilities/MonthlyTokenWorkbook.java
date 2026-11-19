package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.excel.ExcelImportLimits;
import com.pluto.ndi.shared.excel.ExcelWorkbook;

import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

final class MonthlyTokenWorkbook {
    private static final int MAX_IMAGE_BYTES = 40 * 1024 * 1024;
    private static final Pattern AMOUNT = Pattern.compile("^(1[0-2]|[1-9])月Token量[（(]万[）)]$");
    private static final Pattern SCREENSHOT = Pattern.compile("^(?:(1[0-2]|[1-9])月)?Token量[-－—]截图$");
    private static final Pattern IMAGE_FORMULA =
            Pattern.compile("^(?:_xlfn\\.)?DISPIMG\\(\"(ID_[A-Za-z0-9_-]+)\",\\s*[01]\\)$");
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    record Image(String type, byte[] data) {}
    record Line(int row, int month, String province, String agent, String project, String systemId,
            String amount, Image image, Integer screenshotColumn, String warning, String error) {}
    record Content(String sheet, List<String> sheets, List<Line> lines) {}

    static boolean isMonthlyColumn(String header) {
        var title = header.replaceAll("\\s+", "");
        return AMOUNT.matcher(title).matches() || SCREENSHOT.matcher(title).matches();
    }

    static Content read(byte[] bytes, String sheetName) throws IOException {
        if (bytes.length == 0 || bytes.length > ExcelImportLimits.MAX_BYTES) throw invalid("工作簿不得超过 20 MB");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheets = new ArrayList<String>();
            for (int i = 0; i < book.getNumberOfSheets(); i++) {
                if (!book.isSheetHidden(i) && !book.isSheetVeryHidden(i)) sheets.add(book.getSheetName(i));
            }
            if (sheets.isEmpty() || sheets.size() > 50) throw invalid("请选择有效的工作表");
            var selected = sheetName == null || sheetName.isBlank() ? sheets.getFirst() : sheetName;
            if (!sheets.contains(selected)) throw invalid("请选择有效的工作表");
            var sheet = book.getSheet(selected);
            var formatter = new DataFormatter(Locale.CHINA);
            var header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() > 128)
                throw invalid("首行须为表头，最多支持 128 列");
            var amounts = new LinkedHashMap<Integer, Integer>();
            var screenshots = new HashMap<Integer, Integer>();
            var names = new HashMap<String, Integer>();
            for (var cell : header) {
                var title = formatter.formatCellValue(cell).replaceAll("\\s+", "");
                int column = cell.getColumnIndex();
                var amount = AMOUNT.matcher(title);
                var screenshot = SCREENSHOT.matcher(title);
                if (amount.matches()) {
                    if (amounts.putIfAbsent(Integer.parseInt(amount.group(1)), column) != null)
                        throw invalid("同一月份存在多个用量列");
                } else if (screenshot.matches()) {
                    var month = screenshot.group(1) == null
                            ? amounts.entrySet().stream().filter(e -> e.getValue() == column - 1)
                                    .map(Map.Entry::getKey).findFirst().orElse(null)
                            : Integer.valueOf(screenshot.group(1));
                    if (month == null || screenshots.putIfAbsent(month, column) != null)
                        throw invalid("截图列无法唯一匹配月份");
                } else if (List.of("省分", "省份", "智能体名称", "应用项目名称", "系统ID", "智能体ID").contains(title)
                        && names.putIfAbsent(title, column) != null) {
                    throw invalid("智能体匹配列重复");
                }
            }
            var province = names.getOrDefault("省分", names.get("省份"));
            var agent = names.get("智能体名称");
            var project = names.get("应用项目名称");
            var id = names.getOrDefault("系统ID", names.get("智能体ID"));
            if (amounts.isEmpty() || id == null && (province == null || agent == null))
                throw invalid("首行须包含用量列及系统 ID，或包含用量列、省分和智能体名称");
            if (!amounts.keySet().containsAll(screenshots.keySet()))
                throw invalid("存在缺少用量列的截图月份");
            var images = embeddedImages(book);
            var result = new ArrayList<Line>();
            int dataRows = 0;
            for (var row : sheet) {
                if (row.getRowNum() == 0 || !ExcelWorkbook.hasData(row)) continue;
                ExcelImportLimits.checkRowCount(++dataRows);
                for (var entry : amounts.entrySet()) {
                    var amount = get(row, entry.getValue());
                    var shot = get(row, screenshots.get(entry.getKey()));
                    var value = text(amount, formatter);
                    var formula = shot != null && shot.getCellType() == CellType.FORMULA;
                    var screenshot = text(shot, formatter);
                    if (value.isEmpty() && screenshot.isEmpty() && !formula) continue;
                    String warning = "", error = "";
                    Image image = null;
                    if (formula) {
                        var matcher = IMAGE_FORMULA.matcher(shot.getCellFormula());
                        if (!matcher.matches() || (image = images.get(matcher.group(1))) == null)
                            error = "无法读取截图，请检查内嵌图片";
                    } else if (!screenshot.isEmpty()) {
                        warning = "截图列中的文字仅作预检提示：" + screenshot;
                    }
                    if (value.isEmpty()) {
                        if (!formula) continue;
                        error = "有截图时须填写 Token 量";
                    } else if (amount.getCellType() == CellType.FORMULA) {
                        error = "Token 量须填写数值，公式请粘贴为值";
                    }
                    result.add(new Line(row.getRowNum() + 1, entry.getKey(), text(get(row, province), formatter),
                            text(get(row, agent), formatter), text(get(row, project), formatter), text(get(row, id), formatter),
                            value, image, screenshots.get(entry.getKey()), warning, error));
                }
            }
            if (result.isEmpty()) throw invalid("工作表没有可预检的月度数据");
            return new Content(selected, sheets, result);
        }
    }

    private static Map<String, Image> embeddedImages(XSSFWorkbook book) throws IOException {
        try {
            var part = book.getPackage().getPart(PackagingURIHelper.createPartName("/xl/cellimages.xml"));
            if (part == null) return Map.of();
            var images = new HashMap<String, Image>();
            var cache = new HashMap<String, Image>();
            try (var stream = part.getInputStream()) {
                var xml = XMLHelper.newDocumentBuilder().parse(stream);
                var pictures = xml.getElementsByTagNameNS("*", "pic");
                int total = 0;
                for (int i = 0; i < pictures.getLength(); i++) {
                    var picture = (Element) pictures.item(i);
                    var property = (Element) picture.getElementsByTagNameNS("*", "cNvPr").item(0);
                    var blip = (Element) picture.getElementsByTagNameNS("*", "blip").item(0);
                    if (property == null || blip == null) continue;
                    var key = blip.getAttributeNS(REL_NS, "embed");
                    var relation = part.getRelationship(key);
                    if (relation == null || relation.getTargetMode() != org.apache.poi.openxml4j.opc.TargetMode.INTERNAL)
                        continue;
                    var file = part.getRelatedPart(relation);
                    var type = file.getContentType();
                    if (!Set.of("image/png", "image/jpeg").contains(type)) continue;
                    var image = cache.get(key);
                    if (image == null) {
                        try (var content = file.getInputStream()) {
                            var data = content.readNBytes(MAX_IMAGE_BYTES - total + 1);
                            total += data.length;
                            if (total > MAX_IMAGE_BYTES) throw invalid("内嵌截图解压后合计不得超过 40 MB");
                            var signature = HexFormat.of().parseHex(type.equals("image/png") ? "89504e470d0a1a0a" : "ffd8ff");
                            if (!Arrays.equals(signature, Arrays.copyOf(data, signature.length))) continue;
                            image = new Image(type, data);
                            cache.put(key, image);
                        }
                    }
                    images.put(property.getAttribute("name"), image);
                }
            }
            return images;
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException | org.xml.sax.SAXException e) {
            throw new IOException("无法读取内嵌截图", e);
        }
    }

    private static Cell get(Row row, Integer column) {
        return column == null ? null : row.getCell(column);
    }

    private static String text(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        return formatter.formatCellValue(cell).strip();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
