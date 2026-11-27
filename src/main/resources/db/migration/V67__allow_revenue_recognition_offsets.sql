-- Excel 导入的计收冲减允许负认定金额，手工认定仍由业务校验限制为正数。
ALTER TABLE revenue_entry DROP CHECK ck_revenue_recognition;
