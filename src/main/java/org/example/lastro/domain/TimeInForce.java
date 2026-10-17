package org.example.lastro.domain;

/** Ate quando a ordem permanece valida, conforme padrao de mercado. */
public enum TimeInForce {
    /** Good Till Cancelled: descansa no book ate ser executada ou cancelada. */
    GTC,
    /** Immediate Or Cancel: executa o que puder agora e cancela o restante. */
    IOC,
    /** Fill Or Kill: executa integralmente agora ou e cancelada sem execucao. */
    FOK
}
