# 机械动力火炮：载人航天
### Create Big cannon:Artillery-Powered Space Program

![logo](src/main/resources/logo.png)

> 💡 灵感来自《战争雷霆》 · Вдохновлено игрой War Thunder · Inspired by War Thunder

---

## 中文

### 简介
《机械动力火炮：载人航天》是《机械动力》(Create) 与《机械动力：火炮》(Create Big Cannons) 的兼容扩展 mod，**弹药殉爆机制灵感来自《战争雷霆》(War Thunder)**。

它修复了 CBC 军事补充包 (CBCMS, `cbcmoreshells`) 的水套弹药架与 CBC 现代战争 (CBCMW, `cbcmodernwarfare`) 中炮弹药之间的交互问题，并加入弹药殉爆机制。

### 功能
- **动力臂装填修复**：动力臂现在可以从水套弹药架中取出/放入 CBCMW 中炮弹药。
- **红石弹种选择修复**：水套弹药架由红石信号控制的弹种选择支持 CBCMW 弹药。
- **弹药殉爆**（灵感来自战争雷霆）：
  - 装有弹药的弹药架或置物台被任意 CBC 系炮弹（CBC/CBCMW/CBCMS 及继承 CBC 炮弹类的其他拓展）直接命中，必定殉爆；
  - 被任意爆炸波及也必定殉爆；
  - 一次殉爆为多次爆炸，威力提升（次数、间隔、半径可配置）；
  - 面包学 (Mianbaos Modern Warfare) 与 Vestalihy 的导弹/火箭发射器及飞行中的导弹被命中也会殉爆（威力稍小）；飞行中导弹被破片命中也会殉爆；空发射器不会殉爆；
  - 携带 CBC 弹药的玩家死亡时会殉爆（威力与弹药架相同）；
  - 修复弹药架连环殉爆导致游戏崩溃的问题。

### 安装
1. 安装下方的前置 mod。
2. 将仓库根目录的 `cbcmsmwcompat-1.2.0.jar` 放入对应游戏实例的 `mods` 文件夹。
3. 首次启动后生成配置文件：`world/serverconfig/cbcmsmwcompat-server.toml`。

### 前置要求
- Minecraft 1.21.1，NeoForge ≥ 21.1.233
- Create ≥ 6.0.10（< 6.1.0）
- Create Big Cannons ≥ 5.11.7
- CBC-Military-Supplement ≥ 2.1.0
- cbcmodernwarfare ≥ 0.0.6v
- 可选：mianbaos_modernwarfare ≥ 2.3.0（面包学联动）
- 可选：vestalihy ≥ 2.5.2（Vestalihy 联动）

### 构建
在 PowerShell 中运行 `build.ps1`。脚本会自动定位装有 CBCMW、CBCMS、Create、CBC 5.11.7 与航空学修复 mod 的游戏实例，解包 Create 的 jar-in-jar 依赖，使用 Minecraft 自带 Java 运行时编译并打包。

---

## Русский

### Описание
«Create Big cannon: Artillery-Powered Space Program» — это аддон для Create и Create Big Cannons. **Механика детонации боезапаса вдохновлена игрой War Thunder.**

Мод исправляет взаимодействие стеллажей с водяным охлаждением из CBC Military Supplement (CBCMS, `cbcmoreshells`) с боеприпасами среднего калибра из CBC Modern Warfare (CBCMW, `cbcmodernwarfare`) и добавляет механику детонации боезапаса.

### Возможности
- **Исправлена загрузка механической рукой**: рука может доставать и класть боеприпасы CBCMW в стеллажи с водяным охлаждением.
- **Исправлен выбор типа снаряда**: переключение снарядов по сигналу редстоуна поддерживает боеприпасы CBCMW.
- **Детонация боезапаса** (вдохновлена War Thunder):
  - стеллаж или депо с боеприпасами гарантированно детонирует при прямом попадании любого снаряда семейства CBC (CBC, CBCMW, CBCMS и другие аддоны, наследующие классы снарядов CBC);
  - также гарантированно детонирует при попадании в зону любого взрыва;
  - одна детонация — это несколько взрывов повышенной мощности (количество, интервал и радиус настраиваются);
  - пусковые установки ракет Mianbaos Modern Warfare и Vestalihy, а также ракеты в полёте детонируют при попадании (мощность ниже); летящая ракета детонирует и от осколков; пустые пусковые установки не детонируют;
  - игрок с боеприпасами CBC детонирует при смерти (мощность как у стеллажа);
  - исправлен вылет игры при цепной детонации стеллажей.

### Установка
1. Установите зависимости (см. ниже).
2. Скопируйте `cbcmsmwcompat-1.2.0.jar` из корня репозитория в папку `mods` нужного экземпляра игры.
3. После первого запуска создаётся конфиг: `world/serverconfig/cbcmsmwcompat-server.toml`.

### Требования
- Minecraft 1.21.1, NeoForge ≥ 21.1.233
- Create ≥ 6.0.10 (< 6.1.0)
- Create Big Cannons ≥ 5.11.7
- CBC-Military-Supplement ≥ 2.1.0
- cbcmodernwarfare ≥ 0.0.6v
- Опционально: mianbaos_modernwarfare ≥ 2.3.0, vestalihy ≥ 2.5.2

### Сборка
Запустите `build.ps1` в PowerShell. Скрипт сам найдёт игровой экземпляр с нужными модами, распакует jar-in-jar библиотеки Create, скомпилирует и упакует мод.

---

## English

### About
Create Big cannon: Artillery-Powered Space Program is an add-on for Create and Create Big Cannons. **Its ammunition cook-off mechanics are inspired by War Thunder.**

It fixes interaction between the water-jacketed ammo racks from CBC Military Supplement (CBCMS, `cbcmoreshells`) and medium cannon ammunition from CBC Modern Warfare (CBCMW, `cbcmodernwarfare`), and adds ammunition cook-off mechanics.

### Features
- **Mechanical arm loading fix**: Create mechanical arms can now load and unload CBCMW medium cannon ammunition from water-jacketed ammo racks.
- **Redstone shell selection fix**: the racks' redstone-controlled shell selection also cycles CBCMW ammunition.
- **Ammunition cook-off** (inspired by War Thunder):
  - a rack or depot holding ammunition always cook offs when directly hit by any CBC-family projectile (CBC, CBCMW, CBCMS and other add-ons extending CBC projectile classes);
  - it also always cook offs when caught in any explosion blast;
  - one cook-off is a burst of several explosions with increased power (count, interval and radius are configurable);
  - missile/rocket launchers and in-flight missiles from Mianbaos Modern Warfare and Vestalihy also detonate when hit (slightly weaker); in-flight missiles detonate from fragments too; empty launchers do not detonate;
  - players carrying CBC ammunition detonate on death (same power as a rack);
  - fixed a crash caused by chain cook-offs between racks.

### Install
1. Install the dependencies listed below.
2. Drop `cbcmsmwcompat-1.2.0.jar` from the repository root into the `mods` folder of your game instance.
3. After the first launch a config file is generated: `world/serverconfig/cbcmsmwcompat-server.toml`.

### Requirements
- Minecraft 1.21.1, NeoForge ≥ 21.1.233
- Create ≥ 6.0.10 (< 6.1.0)
- Create Big Cannons ≥ 5.11.7
- CBC-Military-Supplement ≥ 2.1.0
- cbcmodernwarfare ≥ 0.0.6v
- Optional: mianbaos_modernwarfare ≥ 2.3.0 (Mianbaos integration)
- Optional: vestalihy ≥ 2.5.2 (Vestalihy integration)

### Building
Run `build.ps1` in PowerShell. The script locates the game instance containing CBCMW, CBCMS, Create, CBC 5.11.7 and the aeronautics fix, extracts Create's jar-in-jar dependencies, compiles with the bundled Minecraft Java runtime and packages the mod.

---

## 配置 / Настройка / Configuration

配置文件：`world/serverconfig/cbcmsmwcompat-server.toml`

| Key | Default | Meaning / 含义 |
| --- | --- | --- |
| `loading_fix.enabled` | `true` | 动力臂装填修复 / исправление загрузки рукой / arm loading fix |
| `cook_off.directHitEnabled` | `true` | 被直接命中殉爆 / детонация при прямом попадании / cook-off on direct hit |
| `cook_off.blastEnabled` | `true` | 被爆炸波及殉爆 / детонация от взрывной волны / cook-off in explosion blast |
| `cook_off.depotEnabled` | `true` | 置物台殉爆 / детонация депо / depot cook-off |
| `cook_off.explosionCount` | `3` | 单次殉爆爆炸次数 (1-10) / число взрывов / explosions per cook-off |
| `cook_off.explosionInterval` | `4` | 爆炸间隔 (tick) / интервал между взрывами / ticks between explosions |
| `cook_off.explosionJitter` | `1.5` | 爆炸随机偏移 / случайное смещение / max random offset |
| `cook_off.explosionBlockRadius` | `7.0` | 方块破坏半径 / радиус разрушения блоков / block radius |
| `cook_off.explosionEntityRadius` | `7.0` | 实体伤害半径 / радиус урона сущностям / entity damage radius |
| `cook_off.fire` | `false` | 是否生成火焰 / создавать ли огонь / create fire |

---

## 灵感来源 / Вдохновение / Inspiration

弹药殉爆机制（弹药架、置物台、发射器、飞行中导弹与死亡玩家身上的弹药在命中或爆炸波及下起爆）的灵感来自《战争雷霆》(War Thunder)。

Механика детонации боезапаса вдохновлена игрой War Thunder.

The ammunition cook-off mechanics are inspired by War Thunder.

---

License: MIT (see `LICENSE`)