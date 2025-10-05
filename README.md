## distributary

[![License](https://img.shields.io/github/license/Fallen-Breath/distributary.svg)](http://www.gnu.org/licenses/lgpl-3.0.html)
[![Issues](https://img.shields.io/github/issues/Fallen-Breath/distributary.svg)](https://github.com/Fallen-Breath/distributary/issues)
[![MC Versions](http://cf.way2muchnoise.eu/versions/For%20MC_distributary_all.svg)](https://legacy.curseforge.com/minecraft/mc-mods/distributary)
[![CurseForge](http://cf.way2muchnoise.eu/full_distributary_downloads.svg)](https://legacy.curseforge.com/minecraft/mc-mods/distributary)
[![Modrinth](https://img.shields.io/modrinth/dt/UQomx7Ba?label=Modrinth%20Downloads)](https://modrinth.com/mod/distributary)

A Minecraft reversed proxy that distribute connections to backend servers based on the host info in the handshake packet

In brief, this mod can recognize the server address used by the client when connecting to the server and use it as a routing identifier.
When the address matches a given list, this mod will forward the client connection to a specified backend Minecraft server.
At this point, the mod acts as a port forwarding tool.
For those client connections that do not match any of the configured addresses,
they will be directly connected to the current server as if this mod does not exist

```mermaid
graph TD
    svr[Minecraft server with distributary]
    i1[Minecraft Client 1]
    i2[Minecraft Client 2]
    i3[Minecraft Client 3]
    o1[Minecraft Server A]
    o2[Minecraft Server B]

    i1 -->|a.example.com| svr
    i2 -->|b.example.com| svr
    i3 -->|other.example.com| svr
    svr -->|client 1| o1
    svr -->|client 2| o2
    svr -->|client 3| svr
```