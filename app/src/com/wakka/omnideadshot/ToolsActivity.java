package com.wakka.omnideadshot;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeToolsActivity;
public final class ToolsActivity extends BridgeToolsActivity {
    protected BridgeBackend backend() { return DeadshotCatalog.game(this); }
}
