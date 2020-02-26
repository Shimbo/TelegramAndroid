package org.telegram.circles;

import androidx.annotation.StringRes;

import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

public final class RequestError extends Throwable {
    public final TLRPC.TL_error error;
    public final ErrorCode code;

    RequestError(TLRPC.TL_error error) {
        this.error = error;
        code = ErrorCode.TLRPC;
    }

    RequestError(ErrorCode error) {
        this.error = null;
        code = error;
    }

    public enum ErrorCode {
        TLRPC(R.string.circles_telegram_protocol_error),
        BOT_SEED_LOOKUP_FAILED(R.string.circles_bot_seed_id_lookup_failed),
        EMPTY_RESPONSE(R.string.circles_empty_response),
        DIDNT_RECEIVE_TOKEN(R.string.circles_token_retrieve_failed);

        public final int message;

        ErrorCode(@StringRes int message) {
            this.message = message;
        }
    }
}
