package com.aurviz.aiagent

import com.aurviz.aiagent.domain.MultiStepPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiStepPlannerTest {

    private val planner = MultiStepPlanner()

    @Test
    fun `test compound schedule prompt creates multi-step plan`() {
        val prompt = "Every morning at 9 AM, check my important emails and prepare a summary."
        val plan = planner.createPlanForPrompt(prompt, "")

        assertNotNull(plan)
        assertEquals(3, plan.steps.size)

        // Step 1: schedule_task
        assertEquals("schedule_task", plan.steps[0].toolName)
        assertTrue(plan.steps[0].args["cron_schedule"].toString().contains("9 AM", ignoreCase = true))

        // Step 2: email check
        assertEquals("send_email", plan.steps[1].toolName)
        assertEquals("check_inbox", plan.steps[1].args["action"])

        // Step 3: manage_memory
        assertEquals("manage_memory", plan.steps[2].toolName)
    }

    @Test
    fun `test web extraction and email compound workflow`() {
        val prompt = "Look up the latest AI news and email a summary to alice.manager@aurviz.com"
        val memoryContext = "- [CONTACT] key_contact_alice: Alice is the Engineering Manager (alice.manager@aurviz.com)"
        val plan = planner.createPlanForPrompt(prompt, memoryContext)

        assertNotNull(plan)
        assertEquals(2, plan.steps.size)

        // Step 1: web_automation
        assertEquals("web_automation", plan.steps[0].toolName)

        // Step 2: send_email
        assertEquals("send_email", plan.steps[1].toolName)
        assertEquals("alice.manager@aurviz.com", plan.steps[1].args["recipient"])
    }

    @Test
    fun `test direct email command with prefix and dictation`() {
        val prompt = "hello send email to real Kumar SPk 01 from PL Kumar 06@gmail.com and say hi hello how are you what happened and its a time to go thats it"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("send_email", plan.steps[0].toolName)
        assertEquals("send", plan.steps[0].args["action"])
        assertTrue(plan.steps[0].args["body"].toString().contains("hi hello how are you"))
    }

    @Test
    fun `test scheduled email dispatch command`() {
        val prompt = "send an email to 230104043@HBTU.AC.IN same message daily at 10 am"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(3, plan.steps.size)
        assertEquals("schedule_task", plan.steps[0].toolName)
        assertEquals("send_email", plan.steps[1].toolName)
        assertEquals("send", plan.steps[1].args["action"])
        assertEquals("230104043@hbtu.ac.in", plan.steps[1].args["recipient"]?.toString()?.lowercase())
    }

    @Test
    fun `test identity email update command`() {
        val prompt = "set my email as priyalkumar06@gmail.com and always send mail from this only"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("manage_memory", plan.steps[0].toolName)
        assertEquals("save", plan.steps[0].args["action"])
        assertEquals("primary_email", plan.steps[0].args["key"])
        assertTrue(plan.steps[0].args["content"].toString().contains("priyalkumar06@gmail.com"))
    }

    @Test
    fun `test conversational hello message to specific email`() {
        val prompt = "I just want to send a hello message to 230104043@gmaildu.ac.in mail ID only"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("send_email", plan.steps[0].toolName)
        assertEquals("send", plan.steps[0].args["action"])
        assertEquals("230104043@gmaildu.ac.in", plan.steps[0].args["recipient"])
    }

    @Test
    fun `test pure greeting greeting response`() {
        val prompt = "hello"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("manage_memory", plan.steps[0].toolName)
        assertEquals("greeting", plan.steps[0].args["action"])
    }

    @Test
    fun `test topic specific dynamic email generation for leave application`() {
        val prompt = "leave application par email karo to boss@aurviz.com"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("send_email", plan.steps[0].toolName)
        assertEquals("boss@aurviz.com", plan.steps[0].args["recipient"])
        assertTrue(plan.steps[0].args["subject"].toString().contains("Leave", ignoreCase = true))
        assertTrue(plan.steps[0].args["body"].toString().contains("leave of absence", ignoreCase = true))
    }

    @Test
    fun `test topic specific dynamic email generation for fee submission`() {
        val prompt = "fee submission update par email bhejo to 230104043@hbtu.ac.in"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("send_email", plan.steps[0].toolName)
        assertEquals("230104043@hbtu.ac.in", plan.steps[0].args["recipient"])
        assertTrue(plan.steps[0].args["subject"].toString().contains("Fee", ignoreCase = true))
        assertTrue(plan.steps[0].args["body"].toString().contains("fee payment", ignoreCase = true))
    }

    @Test
    fun `test phone call intent recognition`() {
        val prompt = "9876543210 par call lagao"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(2, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("call_phone", plan.steps[0].args["action"])
        assertEquals("9876543210", plan.steps[0].args["phone_number"])
        assertEquals("manage_memory", plan.steps[1].toolName)
    }

    @Test
    fun `test whatsapp message intent recognition`() {
        val prompt = "send hello message to whatsapp"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("send_whatsapp", plan.steps[0].args["action"])
        assertTrue(plan.steps[0].args["message"].toString().contains("hello", ignoreCase = true))
    }

    @Test
    fun `test camera open intent recognition`() {
        val prompt = "open camera"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("open_camera", plan.steps[0].args["action"])
    }

    @Test
    fun `test web automation for custom domain url`() {
        val prompt = "college-study.netlify.app website open krdo"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("web_automation", plan.steps[0].toolName)
        assertEquals("https://college-study.netlify.app", plan.steps[0].args["url"])
    }

    @Test
    fun `test whatsapp message with contact name and custom content in hinglish`() {
        val prompt = "whatsapp open karke papa name se jo contact save hai unpar \"kuch fruits like apple, banana, grapes lane ko hai aaj\" yeh bhejdo"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("send_whatsapp", plan.steps[0].args["action"])
        assertEquals("Papa", plan.steps[0].args["contact_name"])
        assertEquals("kuch fruits like apple, banana, grapes lane ko hai aaj", plan.steps[0].args["message"])
    }

    @Test
    fun `test whatsapp message extraction without quotes for unquoted prompt`() {
        val prompt = "papa ko whatsapp par likhdo fruits le aana"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("send_whatsapp", plan.steps[0].args["action"])
        assertEquals("Papa", plan.steps[0].args["contact_name"])
        assertTrue(plan.steps[0].args["message"].toString().contains("fruits le aana", ignoreCase = true))
    }

    @Test
    fun `test app opening intent for instagram and youtube`() {
        val prompt = "open instagram"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("open_app", plan.steps[0].args["action"])
        assertEquals("instagram", plan.steps[0].args["app_name"])
    }

    @Test
    fun `test app opening intent for zomato and general apps`() {
        val prompt = "open zomato"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("open_app", plan.steps[0].args["action"])
        assertEquals("zomato", plan.steps[0].args["app_name"])
    }

    @Test
    fun `test whatsapp multi-word contact extraction for papa ji jio`() {
        val prompt = "whatsapp par papa ji jio ko msg bhejo hello"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("device_actions", plan.steps[0].toolName)
        assertEquals("send_whatsapp", plan.steps[0].args["action"])
        assertEquals("Papa Ji Jio", plan.steps[0].args["contact_name"])
    }

    @Test
    fun `test academic exam extension email synthesis`() {
        val prompt = "send email to 230104043@hbtu.ac.in on 2 day extension in mid sem examination bcz of large syllabus"
        val plan = planner.createPlanForPrompt(prompt, "")

        assertEquals(1, plan.steps.size)
        assertEquals("send_email", plan.steps[0].toolName)
        val body = plan.steps[0].args["body"].toString()
        assertTrue(body.contains("Mid-Semester Examination", ignoreCase = true))
        assertTrue(body.contains("syllabus", ignoreCase = true))
    }
}
