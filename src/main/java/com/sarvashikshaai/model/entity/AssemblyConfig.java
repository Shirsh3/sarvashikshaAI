package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assembly_config")
@Getter
@Setter
@NoArgsConstructor
public class AssemblyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anthem_url", length = 500)
    private String anthemUrl;

    @Column(name = "prayer_url", length = 500)
    private String prayerUrl;

    @Column(name = "pledge_url", length = 500)
    private String pledgeUrl;

    @Column(name = "hindi_prayer_url", length = 500)
    private String hindiPrayerUrl;
}

