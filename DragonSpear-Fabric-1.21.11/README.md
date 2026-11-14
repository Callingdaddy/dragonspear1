# DragonSpear (Recording Helper) - Fabric 1.21.11

Fabric port of the DragonSpear Paper plugin. Sab kuch server-side hai: players ko koi client mod install nahi karna.

## Build kaise kare

### Option A - GitHub pe (kuch install nahi karna)
1. github.com pe free account banao, naya repository banao.
2. Is folder ke saare files (`.github` folder samet) us repository mein upload karo.
3. Repository ke **Actions** tab mein "build" chalta dikhega (2-5 min).
4. Green hone ke baad us run pe click karo, neeche **Artifacts -> dragonspear-mod** download karo.
5. Zip ke andar `dragonspear-fabric-1.0.0.jar` hai (`-sources.jar` mat lena).

### Option B - Apne PC pe
1. Java 21 JDK install karo (Temurin 21).
2. Gradle 9.2.1 install karo (gradle.org/install).
3. Is folder mein terminal kholo aur chalao: `gradle build`
4. Jar milega: `build/libs/dragonspear-fabric-1.0.0.jar`

## Server pe lagana
1. Fabric server 1.21.11 (loader 0.18.4+) chahiye + **Fabric API 0.141.x for 1.21.11**.
2. Jar ko server ke `mods` folder mein daalo, server restart.
3. Game mein `/rh` (ya `/recorder`, `/rechelp`) - op level 2 chahiye.
4. Data `config/dragonspear/data.json` mein save hota hai (kits, toggles).

## Commands
- `/rh` - main GUI
- `/rh god|immortal|fly|vanish|freeze|keepinv [players]`
- `/rh heal [players]`, `/rh gm <mode> [players]`
- `/rh time <day|night>`, `/rh weather <clear|rain|thunder|unlock>`
- `/rh clear <mobs|items|all>`
- `/rh createkit <name>`, `/rh kit <name> [players]`, `/rh kits`, `/rh delkit <name>`
- `/rh rollback <player>`

## Jo original plugin se alag hai
- Skin Changer: abhi nahi hai (server-side Fabric mein alag, risky kaam hai).
- Player heads GUI mein skin nahi dikhati (sab default head).
- Vanish = invisibility effect (tab list se hide nahi hota).
- Freeze = movement + jump 0 (chalna/kudna band, ghoomna chalu). Flying/elytra se hila ja sakta hai.
- Keep Inventory: marne pe drop hue items turant hata kar respawn pe wapas milte hain.
- Clear Entities: naam wale (named) mobs bacha rehte hain.
