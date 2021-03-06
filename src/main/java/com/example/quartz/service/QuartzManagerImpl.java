package com.example.quartz.service;

import com.example.quartz.config.ExportTask;
import com.example.quartz.utils.TaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @Author: Tengfei.Wang
 * @Description;
 * @Date: Created in 下午5:06 26/10/18
 * @Modified by:
 */
@Component(value = "quartzManager")
@Slf4j
public class QuartzManagerImpl implements QuartzManager {

    @Resource(name = "schedulerFactoryBean")
    private Scheduler scheduler;

    private static final String QUARTZ_FACTORY = "com.example.quartz.config.QuartzFactory";

    @Override
    public boolean addJob(ExportTask scheduleJob) {
        //校验调用方法是否存在
        if(TaskUtils.methodIsExist(scheduleJob.getBeanClass(), scheduleJob.getExecuteMethod(), scheduleJob.getExecuteParam()) == null){
            log.info("定时任务添加失败，执行方法未找到");
            return false;
        }

        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
            /* 获取trigger，即在spring配置文件中定义的 bean id="schedulerFactoryBean" */
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

            if (trigger == null) {
                JobDetail jobDetail = JobBuilder.newJob((Class<? extends Job>) Class.forName(QUARTZ_FACTORY)).withIdentity(scheduleJob.getJobName(), scheduleJob.getJobGroup()).build();
                jobDetail.getJobDataMap().put("scheduleJob", scheduleJob);
                //表达式调度构建器
                CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression());
                //按新的cronExpression表达式构建一个新的trigger
                trigger = TriggerBuilder.newTrigger().withIdentity(scheduleJob.getJobName(), scheduleJob.getJobGroup()).withSchedule(scheduleBuilder).build();
                scheduler.scheduleJob(jobDetail, trigger);
                log.info("定时任务:"+ triggerKey.getGroup() + "." + scheduleJob.getJobName() + ",添加成功");
                return true;
            }

            /* 表达式调度构建器 */
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression());
            /*按新的cronExpression表达式重新构建trigger */
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();
            /*按新的trigger重新设置job执行 */
            scheduler.rescheduleJob(triggerKey, trigger);
            return true;
        } catch (SchedulerException | ClassNotFoundException e) {
            log.error("添加定时任务失败：" + e.getMessage());
            return false;
        }
    }

    @Override
    public void editJob(ExportTask task) {
        TriggerKey triggerKey = TriggerKey.triggerKey(task.getJobName(), task.getJobGroup());
        try {
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                log.error(task.getJobName() + "：未找到");
            }

            assert trigger != null;
            //主要修改cron表达式
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())).build();
            //重启触发器
            scheduler.rescheduleJob(triggerKey, trigger);
        } catch (SchedulerException | NullPointerException e) {
            log.error("定时任务"+ task.getJobName() +"修改失败：" + e.getMessage());
        }
    }

    @Override
    public void executeJob(ExportTask scheduleJob) {
        String jobGroup = scheduleJob.getJobGroup();
        JobKey jobKey = JobKey.jobKey(scheduleJob.getJobName());
        if (!StringUtils.isEmpty(jobGroup)) {
            jobKey = JobKey.jobKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
        }
        try {
            scheduler.triggerJob(jobKey);
        } catch (SchedulerException e) {
            log.error("Task run failed.", e);
        }
    }

    @Override
    public boolean removeJob(String jobName, String jobGroup) {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
        try {
            /* 获取trigger，即在spring配置文件中定义的 bean id="schedulerFactoryBean" */
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

            if (trigger == null) {
                log.error(jobName + "：quartz删除job 参数异常");
            }
            // 停止触发器
            scheduler.pauseTrigger(triggerKey);
            //移除触发器
            scheduler.unscheduleJob(triggerKey);
            //删除任务
            scheduler.deleteJob(JobKey.jobKey(jobName, jobGroup));
            return true;
        } catch (SchedulerException e) {
            log.error(jobName + "：quartz删除job异常");
            return true;
        }
    }
}
