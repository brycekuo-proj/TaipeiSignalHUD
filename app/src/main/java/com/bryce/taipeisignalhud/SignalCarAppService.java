package com.bryce.taipeisignalhud;

import android.content.Intent;

import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

public final class SignalCarAppService extends CarAppService {
    @Override
    public HostValidator createHostValidator() {
        // Road-test APK: allow DHU / Android Auto hosts without maintaining a
        // production Play signing allowlist. Tighten this before public release.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @Override
    public Session onCreateSession() {
        return new Session() {
            @Override
            public Screen onCreateScreen(Intent intent) {
                return new SignalCarScreen(getCarContext());
            }
        };
    }
}
