package com.cronutils.model.time;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import org.junit.Before;
import org.junit.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static com.cronutils.model.CronType.QUARTZ;
import static java.time.ZoneOffset.UTC;
import static org.junit.Assert.*;

/*
 * Copyright 2015 jmrozanec
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ExecutionTimeQuartzIntegrationTest {
    private CronParser parser;
    private static final String EVERY_SECOND = "* * * * * ? *";

    @Before
    public void setUp(){
        parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
    }

    @Test
    public void testForCron() throws Exception {
        assertEquals(ExecutionTime.class, ExecutionTime.forCron(parser.parse(EVERY_SECOND)).getClass());
    }

    @Test
    public void testNextExecutionEverySecond() throws Exception {
        ZonedDateTime now = truncateToSeconds(ZonedDateTime.now());
        ZonedDateTime expected = truncateToSeconds(now.plusSeconds(1));
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(EVERY_SECOND));
        assertEquals(expected, executionTime.nextExecution(now));
    }

    @Test
    public void testTimeToNextExecution() throws Exception {
        ZonedDateTime now = truncateToSeconds(ZonedDateTime.now());
        ZonedDateTime expected = truncateToSeconds(now.plusSeconds(1));
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(EVERY_SECOND));
        assertEquals(Duration.between(now, expected), executionTime.timeToNextExecution(now));
    }

    @Test
    public void testLastExecution() throws Exception {
        ZonedDateTime now = truncateToSeconds(ZonedDateTime.now());
        ZonedDateTime expected = truncateToSeconds(now.minusSeconds(1));
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(EVERY_SECOND));
        assertEquals(expected, executionTime.lastExecution(now));
    }

    @Test
    public void testTimeFromLastExecution() throws Exception {
        ZonedDateTime now = truncateToSeconds(ZonedDateTime.now());
        ZonedDateTime expected = truncateToSeconds(now.minusSeconds(1));
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(EVERY_SECOND));
        assertEquals(Duration.between(expected, now), executionTime.timeToNextExecution(now));
    }

    /**
     * Test for issue #9
     * https://github.com/jmrozanec/cron-utils/issues/9
     * Reported case: If you write a cron expression that contains a month or day of week, nextExection() ignores it.
     * Expected: should not ignore month or day of week field
     */
    @Test
    public void testDoesNotIgnoreMonthOrDayOfWeek(){
        //seconds, minutes, hours, dayOfMonth, month, dayOfWeek
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 11 11 11 11 ?"));
        ZonedDateTime now = ZonedDateTime.of(2015, 4, 15, 0, 0, 0, 0, UTC);
        ZonedDateTime whenToExecuteNext = executionTime.nextExecution(now);
        assertEquals(2015, whenToExecuteNext.getYear());
        assertEquals(11, whenToExecuteNext.getMonthValue());
        assertEquals(11, whenToExecuteNext.getDayOfMonth());
        assertEquals(11, whenToExecuteNext.getHour());
        assertEquals(11, whenToExecuteNext.getMinute());
        assertEquals(0, whenToExecuteNext.getSecond());
    }

    /**
     * Test for issue #18
     * @throws Exception
     */
    @Test
    public void testHourlyIntervalTimeFromLastExecution() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime previousHour = now.minusHours(1);
        String quartzCronExpression = String.format("0 0 %s * * ?", previousHour.getHour());
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(quartzCronExpression));

        assertTrue(executionTime.timeFromLastExecution(now).toMinutes() <= 120);
    }

    /**
     * Test for issue #19
     * https://github.com/jmrozanec/cron-utils/issues/19
     * Reported case: When nextExecution shifts to the 24th hour (e.g. 23:59:59 + 00:00:01), JodaTime will throw an exception
     * Expected: should shift one day
     */
    @Test
    public void testShiftTo24thHour() {
        String expression = "0/1 * * 1/1 * ? *";  // every second every day
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(expression));

        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2016, 8, 5), LocalTime.of(23, 59, 59, 0), UTC);
        ZonedDateTime expected = now.plusSeconds(1);
        ZonedDateTime nextExecution = executionTime.nextExecution(now);

        assertEquals(expected, nextExecution);
    }

    /**
     * Test for issue #19
     * https://github.com/jmrozanec/cron-utils/issues/19
     * Reported case: When nextExecution shifts to 32nd day (e.g. 2015-01-31 23:59:59 + 00:00:01), JodaTime will throw an exception
     * Expected: should shift one month
     */
    @Test
    public void testShiftTo32ndDay() {
        String expression = "0/1 * * 1/1 * ? *";  // every second every day
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(expression));

        ZonedDateTime now = ZonedDateTime.of(2015, 1, 31, 23, 59, 59, 0, UTC);
        ZonedDateTime expected = now.plusSeconds(1);
        ZonedDateTime nextExecution = executionTime.nextExecution(now);

        assertEquals(expected, nextExecution);
    }

    /**
     * Issue #24: next execution not properly calculated
     */
    @Test
    public void testTimeShiftingProperlyDone() throws Exception {
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 0/10 22 ? * *"));
        ZonedDateTime nextExecution = executionTime.nextExecution(ZonedDateTime.now().withHour(15).withMinute(27));
        assertEquals(22, nextExecution.getHour());
        assertEquals(0, nextExecution.getMinute());
    }

    /**
     * Issue #27: execution time properly calculated
     */
    @Test
    public void testMonthRangeExecutionTime(){
        assertNotNull(ExecutionTime.forCron(parser.parse("0 0 0 * JUL-AUG ? *")));
    }

    /**
     * Issue #30: execution time properly calculated
     */
    @Test
    public void testSaturdayExecutionTime(){
        ZonedDateTime now = ZonedDateTime.now();
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 0 3 ? * 6"));
        ZonedDateTime last = executionTime.lastExecution(now);
        ZonedDateTime next = executionTime.nextExecution(now);
        assertNotEquals(last, next);
    }

    /**
     * Issue: execution time properly calculated
     */
    @Test
    public void testWeekdayExecutionTime(){
        ZonedDateTime now = ZonedDateTime.now();
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 0 3 ? * *"));
        ZonedDateTime last = executionTime.lastExecution(now);
        ZonedDateTime next = executionTime.nextExecution(now);
        assertNotEquals(last, next);
    }

    /**
     * Issue #64: Incorrect next execution time for ranges
     */
    @Test
    public void testExecutionTimeForRanges(){
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("* 10-20 * * * ? 2099"));
        ZonedDateTime scanTime = ZonedDateTime.parse("2016-02-29T11:00:00.000-06:00");
        ZonedDateTime nextTime = executionTime.nextExecution(scanTime);
        assertNotNull(nextTime);
        assertEquals(10, nextTime.getMinute());
    }

    /**
     * Issue #65: Incorrect last execution time for fixed month
     */
    @Test
    public void testLastExecutionTimeForFixedMonth(){
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 30 12 1 9 ? 2010"));
        ZonedDateTime scanTime = ZonedDateTime.parse("2016-01-08T11:00:00.000-06:00");
        ZonedDateTime lastTime = executionTime.lastExecution(scanTime);
        assertNotNull(lastTime);
        assertEquals(9, lastTime.getMonthValue());
    }

    /**
     * Issue #66: Incorrect Day Of Week processing for Quartz when Month or Year isn't '*'.
     */
    @Test
    public void testNextExecutionRightDoWForFixedMonth(){
        //cron format: s,m,H,DoM,M,DoW,Y
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 * * ? 5 1 *"));
        ZonedDateTime scanTime = ZonedDateTime.parse("2016-03-06T20:17:28.000-03:00");
        ZonedDateTime nextTime = executionTime.nextExecution(scanTime);
        assertNotNull(nextTime);
        assertEquals(DayOfWeek.SUNDAY, nextTime.getDayOfWeek());
    }

    /**
     * Issue #66: Incorrect Day Of Week processing for Quartz when Month or Year isn't '*'.
     */
    @Test
    public void testNextExecutionRightDoWForFixedYear(){
        //cron format: s,m,H,DoM,M,DoW,Y
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse("0 * * ? * 1 2099"));
        ZonedDateTime scanTime = ZonedDateTime.parse("2016-03-06T20:17:28.000-03:00");
        ZonedDateTime nextTime = executionTime.nextExecution(scanTime);
        assertNotNull(nextTime);
        assertEquals(DayOfWeek.SUNDAY, nextTime.getDayOfWeek());
    }

    /**
     * Issue #70: Illegal question mark value on cron pattern assumed valid.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalQuestionMarkValue(){
        ExecutionTime.forCron(parser.parse("0 0 12 1W ? *"));//s,m,H,DoM,M,DoW
    }

    /**
     * Issue #72: Stacktrace printed.
     * TODO: Although test is passing, there is some stacktrace printed indicating there may be something wrong.
     * TODO: We should analyze it and fix the eventual issue.
     */
    @Test//TODO
    public void testNextExecutionProducingInvalidPrintln(){
        String cronText = "0 0/15 * * * ?";
        Cron cron = parser.parse(cronText);
        final ExecutionTime executionTime = ExecutionTime.forCron(cron);
    }

    /**
     * Issue #73: NextExecution not working as expected
     */
    @Test
    public void testNextExecutionProducingInvalidValues(){
        String cronText = "0 0 18 ? * MON";
        Cron cron = parser.parse(cronText);
        final ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.parse("2016-03-18T19:02:51.424+09:00");
        ZonedDateTime next = executionTime.nextExecution(now);
        ZonedDateTime nextNext = executionTime.nextExecution(next);
        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
        assertEquals(DayOfWeek.MONDAY, nextNext.getDayOfWeek());
        assertEquals(18, next.getHour());
        assertEquals(18, nextNext.getHour());
    }

    /**
     * Test for issue #83
     * https://github.com/jmrozanec/cron-utils/issues/83
     * Reported case: Candidate values are false when combining range and multiple patterns
     * Expected: Candidate values should be correctly identified
     * @throws Exception
     */
    @Test
    public void testMultipleMinuteIntervalTimeFromLastExecution() {
        String expression = "* 8-10,23-25,38-40,53-55 * * * ? *"; // every second for intervals of minutes
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(expression));

        assertEquals(301, executionTime.timeFromLastExecution(ZonedDateTime.of(LocalDate.now(), LocalTime.of(3, 1, 0, 0), UTC)).getSeconds());
        assertEquals(1, executionTime.timeFromLastExecution(ZonedDateTime.of(LocalDate.now(), LocalTime.of(13, 8, 4, 0), UTC)).getSeconds());
        assertEquals(1, executionTime.timeFromLastExecution(ZonedDateTime.of(LocalDate.now(), LocalTime.of(13, 11, 0, 0), UTC)).getSeconds());
        assertEquals(63, executionTime.timeFromLastExecution(ZonedDateTime.of(LocalDate.now(), LocalTime.of(13, 12, 2, 0), UTC)).getSeconds());
    }

    /**
     * Test for issue #83
     * https://github.com/jmrozanec/cron-utils/issues/83
     * Reported case: Candidate values are false when combining range and multiple patterns
     * Expected: Candidate values should be correctly identified
     * @throws Exception
     */
    @Test
    public void testMultipleMinuteIntervalMatch() {
        assertEquals(ExecutionTime.forCron(parser.parse("* * 21-23,0-4 * * ?")).isMatch(ZonedDateTime.of(2014, 9, 20, 20, 0, 0, 0, UTC)), false);
        assertEquals(ExecutionTime.forCron(parser.parse("* * 21-23,0-4 * * ?")).isMatch(ZonedDateTime.of(2014, 9, 20, 21, 0, 0, 0, UTC)), true);
        assertEquals(ExecutionTime.forCron(parser.parse("* * 21-23,0-4 * * ?")).isMatch(ZonedDateTime.of(2014, 9, 20, 0, 0, 0, 0, UTC)), true);
        assertEquals(ExecutionTime.forCron(parser.parse("* * 21-23,0-4 * * ?")).isMatch(ZonedDateTime.of(2014, 9, 20, 4, 0, 0, 0, UTC)), true);
        assertEquals(ExecutionTime.forCron(parser.parse("* * 21-23,0-4 * * ?")).isMatch(ZonedDateTime.of(2014, 9, 20, 5, 0, 0, 0, UTC)), false);
    }

    @Test
    public void testDayLightSavingsSwitch() {
        // every 2 minutes
        String expression = "* 0/2 * * * ?";
        Cron cron = parser.parse(expression);

        // SIMULATE SCHEDULE JUST PRIOR TO DST
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd HH:mm:ss")
                .withZone(ZoneId.of("America/Denver"));
        ZonedDateTime prevRun = ZonedDateTime.parse("2016 03 13 01:59:59", formatter);

        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime nextRun = executionTime.nextExecution(prevRun);
        // Assert we got 3:00am
        assertEquals("Incorrect Hour", 3, nextRun.getHour());
        assertEquals("Incorrect Minute", 0, nextRun.getMinute());

        // SIMULATE SCHEDULE POST DST - simulate a schedule after DST 3:01 with the same cron, expect 3:02
        nextRun = nextRun.plusMinutes(1);
        nextRun = executionTime.nextExecution(nextRun);
        assertEquals("Incorrect Hour", 3, nextRun.getHour());
        assertEquals("Incorrect Minute", 2, nextRun.getMinute());

        // SIMULATE SCHEDULE NEXT DAY DST - verify after midnight on DST switch things still work as expected
        prevRun = ZonedDateTime.parse("2016-03-14T00:00:59Z");
        nextRun = executionTime.nextExecution(prevRun);
        assertEquals("incorrect hour", nextRun.getHour(), 0);
        assertEquals("incorrect minute", nextRun.getMinute(), 2);
    }

    /**
     * Issue #75: W flag not behaving as expected: did not return first workday of month, but an exception
     */
    @Test
    public void testCronWithFirstWorkDayOfWeek() {
        String cronText = "0 0 12 1W * ? *";
        Cron cron = parser.parse(cronText);
        ZonedDateTime dt = ZonedDateTime.parse("2016-03-29T00:00:59Z");

        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime nextRun = executionTime.nextExecution(dt);
        assertEquals("incorrect Day", nextRun.getDayOfMonth(), 1); // should be April 1st (Friday)
    }

    /**
     * Issue #81: MON-SUN flags are not mapped correctly to 1-7 number representations
     * Fixed by adding shifting function when changing monday position.
     */
    @Test
    public void testDayOfWeekMapping() {
        ZonedDateTime fridayMorning = ZonedDateTime.of(2016, 4, 22, 0, 0, 0, 0, UTC);
        ExecutionTime numberExec = ExecutionTime.forCron(parser.parse("0 0 12 ? * 2,3,4,5,6 *"));
        ExecutionTime nameExec = ExecutionTime.forCron(parser.parse("0 0 12 ? * MON,TUE,WED,THU,FRI *"));
        assertEquals("same generated dates", numberExec.nextExecution(fridayMorning),
                nameExec.nextExecution(fridayMorning));
    }

    /**
     * Issue #91: Calculating the minimum interval for a cron expression.
     */
    @Test
    public void testMinimumInterval() {
        Duration s1 = Duration.ofSeconds(1);
        assertEquals(getMinimumInterval("* * * * * ?"), s1);
        assertEquals("Should ignore whitespace", getMinimumInterval("*   *    *  *       * ?"), s1);
        assertEquals(getMinimumInterval("0/1 * * * * ?"), s1);
        assertEquals(getMinimumInterval("*/1 * * * * ?"), s1);

        Duration s60 = Duration.ofSeconds(60);
        assertEquals(getMinimumInterval("0 * * * * ?"), s60);
        assertEquals(getMinimumInterval("0 */1 * * * ?"), s60);

        assertEquals(getMinimumInterval("0 */5 * * * ?"), Duration.ofSeconds(300));
        assertEquals(getMinimumInterval("0 0 * * * ?"), Duration.ofSeconds(3600));
        assertEquals(getMinimumInterval("0 0 */3 * * ?"), Duration.ofSeconds(10800));
        assertEquals(getMinimumInterval("0 0 0 * * ?"), Duration.ofSeconds(86400));
    }

    /**
     * Issue #114: Describe day of week is incorrect
     */
    @Test
    public void descriptionForExpressionTellsWrongDoW(){
        CronDescriptor descriptor = CronDescriptor.instance();
        Cron quartzCron = parser.parse("0 0 8 ? * SUN *");
        //TODO enable: assertEquals("at 08:00 at Sunday day", descriptor.describe(quartzCron));
    }

    /**
     * Issue #117: Last Day of month Skipped on Quartz Expression: 0 * * ? * *
     */
    @Test
    public void noSpecificDayOfMonth() {
        Cron cron = parser.parse("0 * * ? * *");
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.of(2016, 8, 30, 23, 59, 0,0,ZoneId.of("UTC"));
        ZonedDateTime nextRun = executionTime.nextExecution(now);

        assertEquals(ZonedDateTime.of(2016, 8, 31, 0, 0, 0,0, ZoneId.of("UTC")), nextRun);
    }

    /**
     * nexExecution()
     * throw exceptions when DAY-OF-MONTH field bigger than param month length
     */
    @Test(expected = java.lang.IllegalArgumentException.class)
    public void bigNumbersOnDayOfMonthField(){
        Cron cron = parser.parse("0 0 0 31 * ?");
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.of(2016, 11, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

        //nextRun expected to be  2016-12-31 00:00:00 000
        //quartz-2.2.3 return the right date
        ZonedDateTime nextRun = executionTime.nextExecution(now);

        assertEquals(ZonedDateTime.of(2016, 12, 31, 0, 0, 0, 0, ZoneId.of("UTC")), nextRun);
    }
    @Test
    public void noSpecificDayOfMonthEvaluatedOnLastDay() {
    	Cron cron = parser.parse("0 * * ? * *");
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.of(2016, 8, 31, 10, 10, 0,0,ZoneId.of("UTC"));
        ZonedDateTime nextRun = executionTime.nextExecution(now);

        assertEquals(ZonedDateTime.of(2016, 8, 31, 10, 11, 0, 0, ZoneId.of("UTC")), nextRun);
    }
    
    private Duration getMinimumInterval(String quartzPattern) {
        ExecutionTime et = ExecutionTime.forCron(parser.parse(quartzPattern));
        ZonedDateTime coolDay = ZonedDateTime.of(2016, 1, 1, 0, 0, 0, 0, UTC);
        // Find next execution time #1
        ZonedDateTime t1 = et.nextExecution(coolDay);
        // Find next execution time #2 right after #1, the interval between them is minimum
        return et.timeToNextExecution(t1);
    }

    private ZonedDateTime truncateToSeconds(ZonedDateTime dateTime){
        return dateTime.truncatedTo(ChronoUnit.SECONDS);
    }
}