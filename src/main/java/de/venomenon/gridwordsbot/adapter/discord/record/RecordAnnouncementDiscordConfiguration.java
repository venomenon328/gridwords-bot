package de.venomenon.gridwordsbot.adapter.discord.record;

import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(JDA.class)
class RecordAnnouncementDiscordConfiguration {
    @Bean RecordAnnouncementMessageGateway recordAnnouncementMessageGateway(JDA jda) {
        return new JdaRecordAnnouncementMessageGateway(jda);
    }
}
