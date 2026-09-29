package com.tacz.guns.client.input;
import com.tacz.guns.network.NetworkHandler;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

public class ZoomKey {
    public static final KeyMapping ZOOM_KEY = new KeyMapping("key.tacz.zoom.desc",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_V,
            TaCZKeyCategory.TACZ);

    public static void onZoomKeyPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == InputConstants.PRESS && ZOOM_KEY.matches(InputConstants.Type.KEYBOARD.getOrCreate(event.getKey()))) {
            doZoomLogic();
        }
    }

    public static void onZoomMousePress(InputEvent.MouseButton.Post event) {
        if (isInGame() && event.getAction() == InputConstants.PRESS && ZOOM_KEY.matches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            doZoomLogic();
        }
    }

    public static boolean onZoomControllerPress(boolean isPress) {
        if (isInGame() && isPress) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return false;
            }
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (operator.isAim()) {
                NetworkHandler.sendToServer(new ClientMessagePlayerZoom());
                return true;
            }
        }
        return false;
    }

    private static void doZoomLogic() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator.isAim()) {
            NetworkHandler.sendToServer(new ClientMessagePlayerZoom());
        }
    }
}
