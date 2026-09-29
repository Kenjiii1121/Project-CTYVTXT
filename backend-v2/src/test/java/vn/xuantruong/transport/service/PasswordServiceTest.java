package vn.xuantruong.transport.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService();

    @Test
    void matchesNodeJsScryptHash() {
        String salt = "00112233445566778899aabbccddeeff";
        String nodeHash = "3a7bff4196f62091927f35a4c4821f762d9fdb78a776f04d49e4d278d8a53eb09bef8561f6e1b580ae1a88b81150984ab34c1bbf635e120e6b0c9bf0e52bf27e";

        assertThat(passwords.hash("xuantruong123", salt)).isEqualTo(nodeHash);
        assertThat(passwords.verify("xuantruong123", salt, nodeHash)).isTrue();
        assertThat(passwords.verify("wrong", salt, nodeHash)).isFalse();
    }
}
