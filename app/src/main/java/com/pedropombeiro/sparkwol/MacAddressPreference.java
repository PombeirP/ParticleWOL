package com.pedropombeiro.sparkwol;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;

/**
 * Created by Pedro on 14.01.2015.
 */
public class MacAddressPreference extends EditTextPreference {

    public MacAddressPreference(Context context) {
        super(context);

        getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        getEditText().setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = (destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend)).toUpperCase();
                    if (!resultingTxt.matches("^[0-9A-F]{1,2}([:-]([0-9A-F]{1,2}([:-]([0-9A-F]{1,2}([:-]([0-9A-F]{1,2}([:-]([0-9A-F]{1,2}([:-]([0-9A-F]{1,2})?)?)?)?)?)?)?)?)?)?")) {
                        return "";
                    }
                    return null;
                }
                return null;
            }
        }
        });

        getEditText().addTextChangedListener(new TextWatcher() {
            boolean deleting = false;
            int lastCount = 0;

            @Override
            public void afterTextChanged(Editable s) {
                if (!deleting) {
                    String working = s.toString();
                    String[] split = working.split(":");
                    String string = split[split.length - 1];
                    if (string.length() == 2) {
                        s.append(':');
                        return;
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (lastCount < count) {
                    deleting = false;
                } else {
                    deleting = true;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing happens here
            }
        });
    }
}

