export type BackendErrorCode =
    | "AI_TIMEOUT"
    | "AI_UNAVAILABLE"
    | "AI_INVALID_RESPONSE";

export class BackendError extends Error {
    constructor(
        public readonly code: BackendErrorCode,
        public readonly status: number,
        public readonly publicMessage: string,
        options?: ErrorOptions
    ) {
        super(publicMessage, options);
        this.name = "BackendError";
    }
}
