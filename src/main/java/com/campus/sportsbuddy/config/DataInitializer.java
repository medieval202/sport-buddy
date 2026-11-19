package com.campus.sportsbuddy.config;

import com.campus.sportsbuddy.model.Activity;
import com.campus.sportsbuddy.model.ActivityStatus;
import com.campus.sportsbuddy.model.Announcement;
import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.model.Venue;
import com.campus.sportsbuddy.repository.ActivityRepository;
import com.campus.sportsbuddy.repository.AnnouncementRepository;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.repository.VenueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDemoData(
            UserRepository userRepository,
            ActivityRepository activityRepository,
            VenueRepository venueRepository,
            AnnouncementRepository announcementRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            User admin = createUser(
                    userRepository, passwordEncoder, "admin", "admin123", "系统管理员",
                    Role.ADMIN, "跑步", SkillLevel.ADVANCED, "体育中心",
                    "MORNING,AFTERNOON,EVENING", 31.2298, 121.4690, 100
            );
            User demo = createUser(
                    userRepository, passwordEncoder, "demo", "demo123", "陈同学",
                    Role.STUDENT, "跑步,羽毛球", SkillLevel.INTERMEDIATE, "东区",
                    "EVENING,WEEKEND", 31.2310, 121.4730, 96
            );
            User lin = createUser(
                    userRepository, passwordEncoder, "lin", "lin123", "林同学",
                    Role.STUDENT, "跑步,篮球", SkillLevel.INTERMEDIATE, "东区",
                    "EVENING,WEEKEND", 31.2322, 121.4750, 92
            );
            User xiaoyu = createUser(
                    userRepository, passwordEncoder, "xiaoyu", "xiaoyu123", "小雨",
                    Role.STUDENT, "羽毛球,乒乓球", SkillLevel.INTERMEDIATE, "西区",
                    "AFTERNOON,WEEKEND", 31.2278, 121.4682, 100
            );
            User haoran = createUser(
                    userRepository, passwordEncoder, "haoran", "haoran123", "浩然",
                    Role.STUDENT, "跑步,足球", SkillLevel.ADVANCED, "南区",
                    "MORNING,EVENING", 31.2235, 121.4778, 87
            );
            User yue = createUser(
                    userRepository, passwordEncoder, "yue", "yue123", "小月",
                    Role.STUDENT, "健身,瑜伽", SkillLevel.BEGINNER, "北区",
                    "MORNING,AFTERNOON", 31.2390, 121.4700, 74
            );
            createUser(
                    userRepository, passwordEncoder, "zixin", "demo123", "赵子欣",
                    Role.STUDENT, "跑步,羽毛球", SkillLevel.INTERMEDIATE, "北区",
                    "EVENING,WEEKEND", 31.2362, 121.4718, 94
            );
            createUser(
                    userRepository, passwordEncoder, "yifan", "demo123", "李一帆",
                    Role.STUDENT, "跑步,篮球", SkillLevel.BEGINNER, "东区",
                    "EVENING,WEEKEND", 31.2331, 121.4769, 88
            );
            createUser(
                    userRepository, passwordEncoder, "zhoumeng", "demo123", "周梦",
                    Role.STUDENT, "羽毛球,健身", SkillLevel.INTERMEDIATE, "西区",
                    "AFTERNOON,WEEKEND", 31.2286, 121.4668, 91
            );
            createUser(
                    userRepository, passwordEncoder, "wangjun", "demo123", "王俊",
                    Role.STUDENT, "跑步,足球", SkillLevel.ADVANCED, "南区",
                    "EVENING,WEEKEND", 31.2248, 121.4768, 82
            );

            LocalDateTime base = LocalDateTime.now().plusDays(1);

            Activity running = createActivity(
                    activityRepository, lin, "东操场夜跑搭子", "跑步",
                    "轻松配速 5'30''，跑后一起拉伸。", "东区", "东操场",
                    base.withHour(19).withMinute(30).withSecond(0).withNano(0),
                    base.withHour(20).withMinute(30).withSecond(0).withNano(0),
                    8, SkillLevel.INTERMEDIATE, 70, 31.2328, 121.4758,
                    ActivityStatus.APPROVED
            );
            running.getParticipants().add(demo);
            activityRepository.save(running);

            LocalDateTime badmintonTime = LocalDateTime.now().plusDays(2)
                    .withHour(15).withMinute(0).withSecond(0).withNano(0);
            createActivity(
                    activityRepository, xiaoyu, "周末羽毛球双打", "羽毛球",
                    "已有球拍和球，缺两位双打搭子，新手友好。", "西区", "风雨球馆 2 号场",
                    badmintonTime, badmintonTime.plusHours(2),
                    4, SkillLevel.BEGINNER, 60, 31.2281, 121.4669,
                    ActivityStatus.APPROVED
            );

            LocalDateTime basketballTime = LocalDateTime.now().plusDays(3)
                    .withHour(18).withMinute(30).withSecond(0).withNano(0);
            createActivity(
                    activityRepository, haoran, "篮球半场 3V3", "篮球",
                    "还差两人，有基本运球和投篮基础即可。", "南区", "南区篮球场",
                    basketballTime, basketballTime.plusHours(2),
                    6, SkillLevel.INTERMEDIATE, 80, 31.2240, 121.4760,
                    ActivityStatus.APPROVED
            );

            LocalDateTime pendingTime = LocalDateTime.now().plusDays(4)
                    .withHour(20).withMinute(0).withSecond(0).withNano(0);
            createActivity(
                    activityRepository, demo, "校园荧光夜跑", "跑步",
                    "沿环校步道慢跑 5 公里，欢迎一起参加。", "主校区", "图书馆南门",
                    pendingTime, pendingTime.plusHours(1),
                    12, SkillLevel.BEGINNER, 60, 31.2304, 121.4737,
                    ActivityStatus.PENDING
            );

            venueRepository.save(createVenue(
                    "东区篮球馆", "东区", "东区体育中心一层", LocalTime.of(8, 0), LocalTime.of(22, 0), true
            ));
            venueRepository.save(createVenue(
                    "西区羽毛球馆", "西区", "西区风雨操场旁", LocalTime.of(7, 0), LocalTime.of(22, 0), true
            ));
            venueRepository.save(createVenue(
                    "南区足球场", "南区", "南区田径场东侧", LocalTime.of(6, 0), LocalTime.of(21, 30), false
            ));
            venueRepository.save(createVenue(
                    "东操场", "东区", "学生公寓 6 号楼北侧", LocalTime.of(6, 0), LocalTime.of(22, 30), false
            ));

            saveAnnouncement(
                    announcementRepository, admin, "本周运动场馆开放安排",
                    "东区篮球馆周三晚进行照明检修，请同学们提前调整预约时间。"
            );
            saveAnnouncement(
                    announcementRepository, admin, "信用积分规则说明",
                    "准时参加活动、主动完成组队可获得信用加分；无故缺席或恶意占用场地会被扣分。"
            );
        };
    }

    private User createUser(
            UserRepository repository,
            PasswordEncoder encoder,
            String username,
            String password,
            String displayName,
            Role role,
            String sports,
            SkillLevel level,
            String zone,
            String timeSlots,
            double latitude,
            double longitude,
            int credit
    ) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setSportPreferences(sports);
        user.setSkillLevel(level);
        user.setCampusZone(zone);
        user.setAvailableTimeSlots(timeSlots);
        user.setLatitude(latitude);
        user.setLongitude(longitude);
        user.setCreditScore(credit);
        user.setEnabled(true);
        return repository.save(user);
    }

    private Activity createActivity(
            ActivityRepository repository,
            User creator,
            String title,
            String sport,
            String description,
            String zone,
            String location,
            LocalDateTime start,
            LocalDateTime end,
            int maxParticipants,
            SkillLevel level,
            int minCredit,
            double latitude,
            double longitude,
            ActivityStatus status
    ) {
        Activity activity = new Activity();
        activity.setCreator(creator);
        activity.setTitle(title);
        activity.setSportType(sport);
        activity.setDescription(description);
        activity.setCampusZone(zone);
        activity.setLocationName(location);
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setMaxParticipants(maxParticipants);
        activity.setRequiredSkillLevel(level);
        activity.setMinCreditScore(minCredit);
        activity.setLatitude(latitude);
        activity.setLongitude(longitude);
        activity.setStatus(status);
        return repository.save(activity);
    }

    private Venue createVenue(
            String name,
            String zone,
            String address,
            LocalTime open,
            LocalTime close,
            boolean indoor
    ) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setCampusZone(zone);
        venue.setAddress(address);
        venue.setOpenTime(open);
        venue.setCloseTime(close);
        venue.setIndoor(indoor);
        venue.setEnabled(true);
        return venue;
    }

    private void saveAnnouncement(
            AnnouncementRepository repository,
            User author,
            String title,
            String content
    ) {
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setCreatedBy(author);
        repository.save(announcement);
    }
}
