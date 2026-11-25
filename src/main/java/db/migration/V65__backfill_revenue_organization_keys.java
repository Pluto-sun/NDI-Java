package db.migration;

import com.pluto.ndi.revenue.util.RevenueOrganizationNames;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 仅写入组织匹配键，保留历史来源字段。 */
public class V65__backfill_revenue_organization_keys extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var read = connection.prepareStatement("""
                SELECT id, JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."地市/组织名称"'))
                FROM revenue_entry
                WHERE id > ? AND specific_organization=TRUE
                  AND JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."省份名称"')) IN ('具体组织', '--')
                ORDER BY id LIMIT 1000
                """);
                var write = connection.prepareStatement("""
                UPDATE revenue_entry SET source_organization_key=? WHERE id=?
                """)) {
            byte[] cursor = new byte[16];
            int count;
            do {
                count = 0;
                read.setBytes(1, cursor);
                try (var rows = read.executeQuery()) {
                    while (rows.next()) {
                        count++;
                        cursor = rows.getBytes(1);
                        write.setBytes(1, RevenueOrganizationNames.key(rows.getString(2)));
                        write.setBytes(2, cursor);
                        write.addBatch();
                    }
                }
                if (count > 0) write.executeBatch();
            } while (count == 1000);
        }
    }
}
