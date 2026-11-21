package com.chatssl.anytime.domain;

/** 되돌릴 수 없는 상태만 저장. 모집 중·마감 임박 등은 조회 시점에 계산한다. */
public enum RoomStatus { OPEN, CANCELED, DONE }
