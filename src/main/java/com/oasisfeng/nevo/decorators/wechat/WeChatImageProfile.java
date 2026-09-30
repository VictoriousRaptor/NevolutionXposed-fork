package com.oasisfeng.nevo.decorators.wechat;

/** APK-verified image descriptors. Each component still validates its complete runtime signatures. */
final class WeChatImageProfile {
    final String label, state, simple, flow, result, remote, local, pathOwner, pathMethod, vfs;
    final String message, talker, sender, serverId;
    final String kernel, core, database, coreGet, query, manager, serviceBase, service,
            implementation, downloadGet, downloadInterface, download, callback;

    private WeChatImageProfile(String label, String[] thumbnail, String[] message, String[] download) {
        this.label = label;
        state = thumbnail[0]; simple = thumbnail[1]; flow = thumbnail[2]; result = thumbnail[3];
        remote = thumbnail[4]; local = thumbnail[5]; pathOwner = thumbnail[6];
        pathMethod = thumbnail[7]; vfs = thumbnail[8];
        this.message = message[0]; talker = message[1]; sender = message[2]; serverId = message[3];
        kernel = download[0]; core = download[1]; database = download[2]; coreGet = download[3];
        query = download[4]; manager = download[5]; serviceBase = download[6]; service = download[7];
        implementation = download[8]; downloadGet = download[9]; downloadInterface = download[10];
        this.download = download[11]; callback = download[12];
    }

    static WeChatImageProfile forVersion(String name, long code) {
        if ("8.0.72".equals(name) && code == 3085)
            return new WeChatImageProfile("8.0.72/3085",
                    new String[]{"v65.z", "yh3.f", "b80.m", "y65.b", "x01.e", "p70.d",
                            "m90.b", "oi", "com.tencent.mm.vfs.w6"},
                    new String[]{"com.tencent.mm.storage.f9", "O0", "C0", "I0"},
                    new String[]{"em0.k1", "em0.c0", "s85.a0", "u", "a", "w85.n0", "w85.m",
                            "n70.y", "m70.e", "Bh", "n70.x", "l11.j", "n70.w"});
        if ("8.0.77".equals(name) && code == 3160)
            return new WeChatImageProfile("8.0.77/3160",
                    new String[]{"pb5.z", "mn3.f", "n90.m", "sb5.b", "q31.e", "b90.d",
                            "e41.l0", "N2", "com.tencent.mm.vfs.a7"},
                    new String[]{"com.tencent.mm.storage.e9", "Q0", "A0", "K0"},
                    new String[]{"ho0.j1", "ho0.b0", "ub5.k0", "v", "f", "sd5.n0", "sd5.m",
                            "z80.y", "y80.e", "Wi", "z80.x", "e41.j", "z80.w"});
        if ("8.0.77".equals(name) && code == 3141)
            return new WeChatImageProfile("8.0.77/3141",
                    new String[]{"ld5.z", "gp3.f", "db0.m", "od5.b", "j51.e", "ra0.d",
                            "x51.l0", "d3", "com.tencent.mm.vfs.a7"},
                    new String[]{"com.tencent.mm.storage.e9", "N0", "C0", "J0"},
                    new String[]{"yp0.k1", "yp0.c0", "qd5.k0", "v", "f", "of5.n0", "of5.m",
                            "pa0.y", "oa0.e", "ej", "pa0.x", "x51.j", "pa0.w"});
        return null;
    }
}
