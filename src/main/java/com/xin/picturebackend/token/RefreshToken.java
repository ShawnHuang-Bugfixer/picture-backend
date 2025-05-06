package com.xin.picturebackend.token;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 *
 * @author 黄兴鑫
 * @since 2025/4/25 19:23
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RefreshToken {
    private Long userId;
    private String jti;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
    private Date issuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
    private Date expiresAt;
}
