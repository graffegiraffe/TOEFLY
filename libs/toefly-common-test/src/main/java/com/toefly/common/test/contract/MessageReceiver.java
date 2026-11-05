package com.toefly.common.test.contract;

/** Reads the message a trigger published. */
public interface MessageReceiver {

    /** State before the trigger, so that only messages published by it are read afterwards. */
    Object mark(String topic);

    /** The message of the topic published after {@code mark}; fails when there is none. */
    ContractMessage receive(String topic, Object mark);
}
