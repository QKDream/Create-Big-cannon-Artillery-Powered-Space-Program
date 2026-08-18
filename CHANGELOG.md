# 更新日志

## v2.0.0（2026-08-18）
## v2.0.1
- 修复弹药直接破坏弹药架/置物台不殉爆：改为弹射物接触引爆——每个 CBC 弹丸 tick 后执行接触判定与移动扫掠，不再依赖 CBC 内部穿透流程
- 爆炸波及殉爆方式保留不变

机械动力火炮：载人航天（Create Big cannon: Artillery-Powered Space Program / cbcmsmwcompat）

本大版本合并了 2026-08-18 当天 v1.3.0 → v1.4.4 的全部内容。

## 殉爆机制
- 威力由弹种与数量决定：迫击石弹、穿甲弹、脱壳穿甲弹等无炸药弹头不殉爆；高爆弹/破甲弹等威力更高；CBCMW 中型弹药全部殉爆
- 数量越多威力越大（威力与爆炸半径立方成正比换算），总上限为基础威力的 2 倍（配置 power.maxMultiplier = 2.0）
- 特殊效果殉爆：烟雾弹释放烟幕、燃烧弹引发火焰
- 携带 CBC 类弹药（含发射药）的生物（不止玩家）死亡时殉爆
- 修复水套弹药架/置物台被直接命中只破坏本体而不殉爆，以及弹药架连环殉爆崩溃

## 联动
- Sable：殉爆对爆炸范围内的 Sable 子层级结构造成结构损伤；爆炸位置正确映射到结构的世界位置，可伤及主世界与相邻结构；爆炸波及枚举子层级方块，结构内外保持连环殉爆
- 车万女仆：女仆死亡殉爆时计入其背包（maidInv）中的全部 CBC 弹药，不再只算手中的
- 修复置物台上的发射药（Powder Charge）不殉爆的问题
- mods.toml 增加 sable 可选依赖声明

## 其他
- 修复弹丸方块命中钩子引起的启动崩溃，保留直接命中殉爆与红石弹种选择修复

## 部署与配置
- 最终 jar：cbcmsmwcompat-2.0.0.jar，已部署至《航空学》实例 mods 文件夹
- 实例配置：config/cbcmsmwcompat-server.toml，power.maxMultiplier = 2.0
- 已推送 GitHub：https://github.com/QKDream/Create-Big-cannon-Artillery-Powered-Space-Program