package com.lingxiao.modules.member.oauth;

import lombok.Getter;

/** 一次性 ticket 解析结果；不包含 Token。 */
@Getter
public class MemberOAuthTicket {

    private final String provider;

    private final Long memberId;

    public MemberOAuthTicket(String provider, Long memberId) {
        this.provider = provider;
        this.memberId = memberId;
    }
}
