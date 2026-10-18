package dev.pocketllama;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import dev.pocketllama.voice.VoiceActivity;

/** Preserve the existing launcher component and upgrade path. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this,dev.pocketllama.security.GateSession.isOpen()&&dev.pocketllama.security.Vault.ready(this)?VoiceActivity.class:dev.pocketllama.security.LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
