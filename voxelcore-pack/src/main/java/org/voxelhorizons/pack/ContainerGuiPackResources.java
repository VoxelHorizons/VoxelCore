package org.voxelhorizons.pack;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in fallback artwork and font providers for vanilla chest UI restoration. */
final class ContainerGuiPackResources {
    private static final String GENERIC_27_TOP =
            "iVBORw0KGgoAAAANSUhEUgAAALAAAABVCAMAAADe4J5dAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAASUExURQAAAP///8bGxlVVVTc3NwAAAE5W3eoAAAAGdFJOU///////ALO/pL8AAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAZdEVYdFNvZnR3YXJlAFBhaW50Lk5FVCA1LjEuMTITAUd0AAAAuGVYSWZJSSoACAAAAAUAGgEFAAEAAABKAAAAGwEFAAEAAABSAAAAKAEDAAEAAAACAAAAMQECABEAAABaAAAAaYcEAAEAAABsAAAAAAAAAGAAAAABAAAAYAAAAAEAAABQYWludC5ORVQgNS4xLjEyAAADAACQBwAEAAAAMDIzMAGgAwABAAAAAQAAAAWgBAABAAAAlgAAAAAAAAACAAEAAgAEAAAAUjk4AAIABwAEAAAAMDEwMAAAAADZp5qVybcLXwAAAPhJREFUaEPtmMEOgjAQBUHs//+y+Gyo9mQME9lk5lIyp8nCocvS2lKHtte2Za1DxtufS3Db5/sMvlVh2/bvolCvwTgG0xhMYzCNwTQG07wF32daPwd/NnPwfpn/YL2aMTiAxuAAGoMDaAwOoDE4gMbgABqDA2iqB7fXD9g3rmam4H6vH4C7Qz8HX5k5uE/+AHy5vxmDA2gMDqAxOIDG4AAagwNoDA6gMTiApnowtymcZabgfq8fgLtDPwdfmTm4T/4AfLm/GYMDaAwOoDE4gMbgABqDA2gMDqAxOICmejC3KZxlpuAaGExjMI3BNAbTGExjME294G15ANPxxhtH0uZEAAAAAElFTkSuQmCC";
    private static final String GENERIC_54_TOP =
            "iVBORw0KGgoAAAANSUhEUgAAALAAAACLCAMAAAD21OMCAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAASUExURQAAAP///8bGxlVVVTc3NwAAAE5W3eoAAAAGdFJOU///////ALO/pL8AAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAZdEVYdFNvZnR3YXJlAFBhaW50Lk5FVCA1LjEuMTITAUd0AAAAuGVYSWZJSSoACAAAAAUAGgEFAAEAAABKAAAAGwEFAAEAAABSAAAAKAEDAAEAAAACAAAAMQECABEAAABaAAAAaYcEAAEAAABsAAAAAAAAAGAAAAABAAAAYAAAAAEAAABQYWludC5ORVQgNS4xLjEyAAADAACQBwAEAAAAMDIzMAGgAwABAAAAAQAAAAWgBAABAAAAlgAAAAAAAAACAAEAAgAEAAAAUjk4AAIABwAEAAAAMDEwMAAAAADZp5qVybcLXwAAAY1JREFUeF7tmLFuwzAQxZyk+v9frvt6sNKbDMNEI4BcFHAiTh502cbY1mHstWN7rEPGW7+X4LnP9yf4uQqv1/5dLNRrMI7BNAbTGExjMI3BNG/BX51R5+SfTQ/eH/N/eHyaMTiAxuAAGoMDaAwOoDE4gMbgABqDA2hWDx6/f8C+8WmmBde7fgLuDnVOTpkeXJM/AC/3mjE4gMbgABqDA2gMDqAxOIDG4AAagwNoVg/mNoW7TAuud/0E3B3qnJwyPbgmfwBe7jVjcACNwQE0BgfQGBxAY3AAjcEBNAYH0KwezG0Kd5kWXO/6Cbg71Dk5ZXpwTf4AvNxrxuAAGoMDaAwOoDE4gMbgABqDA2gMDqBZPZjbFO4yLbje9RNwd6hzcsr04Jr8AXi514zBATQGB9AYHEBjcACNwQE0BgfQGBxAs3owtyncZVpwvesn4O5Q5+SU6cE1+QPwcq8ZgwNoDA6gMTiAxuAAGoMDaAwOoDE4gGb1YG5TuMu04DUwmMZgGoNpDKYxmMZgmvWCX9s3dltkr7EkousAAAAASUVORK5CYII=";

    private ContainerGuiPackResources() {}

    static Map<String, byte[]> entries() {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        entries.put("assets/voxelcore/textures/container/gui/generic_27_top.png", decode(GENERIC_27_TOP));
        entries.put("assets/voxelcore/textures/container/gui/generic_54_top.png", decode(GENERIC_54_TOP));
        return entries;
    }

    static String defaultProviders() {
        return bitmap("voxelcore:container/gui/generic_27_top.png", ContainerGuiGlyphs.GENERIC_27_TOP, 85)
                + ",\n    "
                + bitmap("voxelcore:container/gui/generic_54_top.png", ContainerGuiGlyphs.GENERIC_54_TOP, 139);
    }

    private static String bitmap(String file, int codePoint, int height) {
        return "{\"type\":\"bitmap\",\"file\":\"" + file + "\",\"ascent\":"
                + ContainerGuiGlyphs.ASCENT + ",\"height\":" + height
                + ",\"chars\":[\"" + new String(Character.toChars(codePoint)) + "\"]}";
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
