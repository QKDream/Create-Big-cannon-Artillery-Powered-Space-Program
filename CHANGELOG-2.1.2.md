# 载人航天（CBC 现代战争兼容附属）2.1.2 更新日志

> 对应构建：`cbcmsmwcompat-2.1.2.jar`

## 殉爆扩展到面包学全部投掷物

**问题**：JDAM、滑翔炸弹等**无法被殉爆**，而巡航/导弹可以。

**原因**：殉爆的弹药识别只看类名里是否含 `missile` / `rocket` / `agm`：

```java
// MianbaosCompatUtil.isWeaponEntity
return simple.contains("missile") || simple.contains("rocket") || simple.contains("agm");
```

而面包学**所有投掷物的类名都是 `<东西>tanshe`**：

| 类名 | 含 missile/rocket/agm | 修复前会殉爆 |
|---|---|---|
| `Missiletanshe` / `Laserguidedmissiletanshe` | ✓ | ✓ |
| `Cruisemissiletanshe` | ✓（含 missile） | ✓ |
| `Bombtanshe` / `Jdam1tanshe` / `Jdam2tanshe` | ✗ | **✗** |
| `Glidebombtanshe` | ✗ | **✗** |
| `Clusterbombtanshe` / `Earthpenetratorbombtanshe` / `Firebombtanshe` | ✗ | **✗** |

**修复**：把整个 `*tanshe` 家族纳入飞行弹药判定：

```java
return simple.contains("missile")
        || simple.contains("rocket")
        || simple.contains("agm")
        || simple.contains("tanshe");
```

## 生效范围

- **直接命中扫掠**（`sweepMissiles`）：炮弹飞过空中的炸弹/JDAM/滑翔弹 ⇒ 殉爆 ✓
- **破片扫掠**（`sweepFragments`）：破片掠过飞行中的弹药 ⇒ 殉爆 ✓
- **连锁殉爆**：一枚被引爆后，附近的飞行弹药随之殉爆 ✓
- 仍受配置项 `MIANBAOS_COOK_OFF` 控制；**活体**（发射架/炮塔）仍走独立的 `isLauncherMob` 逻辑，不受影响。

## 构建与部署

```
build.ps1 → cbcmsmwcompat-2.1.2.jar → 自动复制到所有含该 mod 的整合包 mods 目录
```
