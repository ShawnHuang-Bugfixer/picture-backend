package com.xin.picturebackend.model.messagequeue.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/13 8:08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage implements Serializable {
    private String email;
    private String code;
}

