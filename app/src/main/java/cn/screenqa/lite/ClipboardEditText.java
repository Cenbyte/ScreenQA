package cn.screenqa.lite;

import android.content.ClipboardManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.PopupMenu;

/** Offers editing commands even when an OEM floating selection toolbar is unavailable. */
// This app uses framework Activity/widgets, with no AppCompat theme or dependency.
@android.annotation.SuppressLint("AppCompatCustomView")
final class ClipboardEditText extends EditText {
    ClipboardEditText(Context context) {
        super(context);
        setLongClickable(true);
        setOnLongClickListener(view -> {
            // A long press without a selected range operates on the whole field.
            if (length() > 0 && getSelectionStart() == getSelectionEnd()) selectAll();
            PopupMenu menu = new PopupMenu(getContext(), this);
            boolean selected = getSelectionStart() >= 0 && getSelectionEnd() >= 0
                    && getSelectionStart() != getSelectionEnd();
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            menu.getMenu().add(0, android.R.id.copy, 0, android.R.string.copy).setEnabled(selected);
            menu.getMenu().add(0, android.R.id.cut, 1, android.R.string.cut).setEnabled(selected);
            menu.getMenu().add(0, android.R.id.paste, 2, android.R.string.paste)
                    .setEnabled(clipboard != null && clipboard.hasPrimaryClip());
            menu.getMenu().add(0, android.R.id.selectAll, 3, android.R.string.selectAll)
                    .setEnabled(length() > 0);
            menu.setOnMenuItemClickListener(item -> onTextContextMenuItem(item.getItemId()));
            menu.show();
            return true;
        });
    }
}
