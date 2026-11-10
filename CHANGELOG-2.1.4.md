# 载人航天（CBC 现代战争兼容附属）2.1.4 更新日志

> 对应构建：`cbcmsmwcompat-2.1.4.jar`

## 修复：面包学的机炮 / 喷火器 / 机枪弹药也会殉爆，引发连环爆炸与卡顿

**现象**：面包学的机炮弹、机枪弹、喷火器火球（以及迫击炮弹、榴弹、近防炮弹）**全都**会殉爆。
一发 CBC 炮弹掠过一串弹幕，就会把整条弹链殉爆；每颗弹各自排队 3 次爆炸，爆炸又去扫下一批弹药，
几秒钟内变成成百上千次爆炸，服务器直接卡死。

**根因**：2.1.3 为了修好"JDAM/滑翔炸弹不殉爆"，把飞行弹药判据放宽成了"类名含 `tanshe`"：

```java
// MianbaosCompatUtil.isWeaponEntity（2.1.3）
return simple.contains("missile")
        || simple.contains("rocket")
        || simple.contains("agm")
        || simple.contains("tanshe");   // ← 面包学所有投掷物都叫 <东西>tanshe
```

面包学是 MCreator 生成的 mod，**口径再小的弹药也是 `<东西>tanshe` 实体**，于是判据把
"整发弹药"和"枪炮弹丸"混成了一类：

| 类名 | 2.1.3 会殉爆 | 实际是什么 |
|---|---|---|
| `AutocannontansheEntity` / `Autocannontanshe2/3` | ✓（误） | 机炮弹 |
| `GunTurrettansheEntity` | ✓（误） | 机枪塔 / 炮塔弹 |
| `CIWStansheEntity` / `CIWSmissile*` | ✓（误） | 近防炮弹 |
| `FireballtansheEntity` | ✓（误） | 喷火器火球 |
| `MortartansheEntity` 等 7 个 | ✓（误） | 迫击炮弹 |
| `Wolvesgrenadetanshe*` / `Clustergrenadetanshe` / `Firesurpportsmokegrenadetanshe` | ✓（误） | 榴弹 / 烟雾榴弹 |
| `C4tanshe*` / `RocketShrapnelEntity` | ✓（误） | 爆炸物 / 破片 |
| `Missiletanshe` / `Bombtanshe` / `Jdam1tanshe` / `Glidebombtanshe` / `*torpedotanshe` | ✓ | 真正的战斗部 |

**修复**：判定改为"**只认真正的战斗部** + 显式排除小口径与破片"两个清单
（`MianbaosCompatUtil.isCookOffMunition`）：

```java
// 白名单：必须有战斗部
COOK_OFF_MUNITION_MARKERS = { "missile", "rocket", "bomb", "torpedo", "jdam", "agm" };

// 黑名单：黑名单优先，即使类名里带导弹/火箭字样也不算（RocketShrapnel）
NON_COOK_OFF_MARKERS = { "autocannon", "machinegun", "gatling", "gattling", "gunturret",
                         "ciws", "fireball", "flame", "mortar", "grenade", "shrapnel",
                         "fragment", "c4", "arrow", "bullet", "smoke", "countermeasure",
                         "drone" };
```

活体发射平台（导弹/火箭炮塔、火箭兵）仍走原来的 `missile` / `rocket` 判定，行为不变。

## 实测判定结果（对机器上三个面包学版本的真实类名逐个跑过编译后的判定）

| 面包学版本 | 类总数 | 2.1.3 判为可殉爆 | 2.1.4 判为可殉爆 | 剔除 |
|---|---|---|---|---|
| 2.5.1 | 160 | 92 | 71 | 23 |
| 2.6.9 | 180 | 108 | 84 | 26 |
| 2.7.0 | 185 | 110 | 89 | 26 |

- 三个版本里"仍在殉爆清单中的机炮/机枪/炮塔/近防炮/喷火器/迫击炮/榴弹/破片/无人机"数量：**0**
- 仍在殉爆清单里的：`*missiletanshe`、`Agm*tanshe`、`*bombtanshe`、`Jdam1/2tanshe`、`Glidebombtanshe`、
  `*torpedotanshe`、`Cruisemissile*`、`Farrocket*`、`RocketHETanshe`、`*rockettanshe` 等（JDAM 与滑翔炸弹不再回退）

## 顺带的卡顿源

`onExplosionStart` / `onExplosionDetonate` 各有一处 `taov sweep` 的 `LOGGER.info`，
**不受 `debugLogging` 控制、也不限速**，每次半径 ≤24 的爆炸都写一行；联爆时刷屏本身就拖慢主线程。
现在两处都改走已有的 `logTaovSweep`（受 `debugLogging` 控制、每秒最多一条）。

## 生效范围

- **直接命中扫掠**（`sweepMissiles`）：炮弹飞过飞行中的导弹/炸弹 ⇒ 殉爆 ✓；飞过机炮/机枪弹幕 ⇒ 不再殉爆 ✓
- **破片扫掠**（`sweepFragments`）：同上
- **爆炸波及**（`ExplosionEvent.Start/Detonate`）：爆炸范围内的整发弹药才殉爆，枪炮弹丸与破片不算
- 仍受配置项 `MIANBAOS_COOK_OFF` 控制

## 构建与部署

```
build.ps1 → cbcmsmwcompat-2.1.4.jar → 自动复制到所有含该 mod 的整合包 mods 目录
```
