package com.openlauncher.app

import com.openlauncher.app.data.*
import org.junit.Assert.*
import org.junit.Test

class WheelStudyCodecTest {
    private fun mapping(channel:Int=5,low:Int=40,mid:Int=100,high:Int=180)=WheelMapping(WheelAdc(channel,low,high,mid,low),"add_volume",true)
    @Test fun absentButtonIsNotLearned() { assertFalse(WheelAdc(5,255,255,255,255).valid()) }
    @Test fun unrelatedChannelIsRejected() { assertFalse(WheelAdc(3,20,30,20,10).valid()) }
    @Test fun negativeValuesAreRejected() { assertFalse(WheelAdc(5,-1,20,20,20).valid()) }
    @Test fun entriesRoundTripPreservesAdcActionsAndRepeat() {
        val value=mapping()
        val ini=WheelStudyCodec.replaceSection("[other]\nkeep=yes\n",WheelStudyCodec.entries(listOf(value)))
        val restored=WheelStudyCodec.read(ini).single()
        assertEquals(value.action,restored.action);assertEquals(value.sample.high,restored.sample.high)
        assertEquals(value.sample.mid,restored.sample.mid);assertEquals(value.sample.low,restored.sample.low)
        assertTrue(restored.repeat)
    }
    @Test fun wheelResetPreservesOtherSections() {
        val updated=WheelStudyCodec.replaceSection("[section_wheel_Study_Key]\n123=old\n[section_ir_study_key]\n7=keep\n[unrelated]\nx=y\n",emptyList())
        assertFalse(updated.contains("123=old"));assertTrue(updated.contains("7=keep"));assertTrue(updated.contains("x=y"))
    }
    @Test fun oldUnknownFormatCannotBeSilentlyOverwritten() {
        assertThrows(IllegalArgumentException::class.java) { WheelStudyCodec.read("[section_wheel_Study_Key]\n123=home\n") }
    }
    @Test fun differentChannelsRemainIndependent() {
        val entries=WheelStudyCodec.entries(listOf(mapping(5),mapping(6)))
        assertEquals(2,entries.size);assertNotEquals(entries[0].first,entries[1].first)
    }
    @Test fun emptyConfigurationCreatesWheelSectionOnly() {
        assertTrue(WheelStudyCodec.replaceSection("",emptyList()).contains("[section_wheel_Study_Key]"))
    }
    @Test fun assigningLongPressPreservesShortAction() {
        val original=mapping()
        val assigned=WheelStudyCodec.assign(listOf(original),original.sample,WheelFunction.VOICE,true).single()
        assertEquals(original.action,assigned.action);assertEquals("voice",assigned.longAction)
    }
    @Test fun assigningShortPressPreservesLongAction() {
        val original=mapping().copy(longAction="voice")
        val assigned=WheelStudyCodec.assign(listOf(original),original.sample,WheelFunction.NEXT,false).single()
        assertEquals("next",assigned.action);assertEquals("voice",assigned.longAction)
    }
    @Test fun longOnlyActionSurvivesIniRoundTrip() {
        val sample=mapping().sample
        val assigned=WheelStudyCodec.assign(emptyList(),sample,WheelFunction.VOICE,true)
        val ini=WheelStudyCodec.replaceSection("",WheelStudyCodec.entries(assigned))
        val restored=WheelStudyCodec.read(ini).single()
        assertEquals("",restored.action);assertEquals("voice",restored.longAction)
    }
    @Test fun relearningLongActionDoesNotEraseAnotherButtonsShortAction() {
        val old=mapping().copy(longAction="voice")
        val sample=old.sample.copy(low=70,mid=120,high=200)
        val assigned=WheelStudyCodec.assign(listOf(old),sample,WheelFunction.VOICE,true)
        assertEquals(2,assigned.size);assertEquals(old.action,assigned.first().action)
        assertEquals("",assigned.first().longAction);assertEquals("voice",assigned.last().longAction)
    }
}
