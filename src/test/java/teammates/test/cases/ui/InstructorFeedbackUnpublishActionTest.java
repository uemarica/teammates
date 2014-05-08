package teammates.test.cases.ui;

import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertEquals;

import java.util.Date;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import teammates.common.datatransfer.DataBundle;
import teammates.common.datatransfer.FeedbackSessionAttributes;
import teammates.common.util.Const;
import teammates.common.util.TimeHelper;
import teammates.storage.api.FeedbackSessionsDb;
import teammates.ui.controller.InstructorFeedbackUnpublishAction;
import teammates.ui.controller.RedirectResult;

public class InstructorFeedbackUnpublishActionTest extends BaseActionTest {
    private static final boolean PUBLISHED = true;
    private static final boolean UNPUBLISHED = false;
    DataBundle dataBundle;
        
    @BeforeClass
    public static void classSetUp() throws Exception {
        printTestClassHeader();
        uri = Const.ActionURIs.INSTRUCTOR_FEEDBACK_UNPUBLISH;
    }

    @BeforeMethod
    public void caseSetUp() throws Exception {
        dataBundle = getTypicalDataBundle();
        restoreTypicalDataInDatastore();
    }
    
    @Test
    public void testAccessControl() throws Exception{
        FeedbackSessionAttributes session = dataBundle.feedbackSessions.get("session1InCourse1");
        
        makeFeedbackSessionPublished(session); //we have to revert to the closed state
        
        String[] submissionParams = new String[]{
                Const.ParamsNames.COURSE_ID, session.courseId,
                Const.ParamsNames.FEEDBACK_SESSION_NAME, session.feedbackSessionName 
        };
        
        verifyUnaccessibleWithoutLogin(submissionParams);
        verifyUnaccessibleForUnregisteredUsers(submissionParams);
        verifyUnaccessibleForStudents(submissionParams);
        verifyUnaccessibleForInstructorsOfOtherCourses(submissionParams);
        verifyAccessibleForInstructorsOfTheSameCourse(submissionParams);
        
        makeFeedbackSessionPublished(session); //we have to revert to the closed state
        
        verifyAccessibleForAdminToMasqueradeAsInstructor(submissionParams);
    }
    
    @Test
    public void testExecuteAndPostProcess() throws Exception{
        gaeSimulation.loginAsInstructor(dataBundle.instructors.get("instructor1OfCourse1").googleId);
        FeedbackSessionAttributes session = dataBundle.feedbackSessions.get("session2InCourse1");
        String[] unpublishParams = {
                Const.ParamsNames.COURSE_ID, session.courseId,
                Const.ParamsNames.FEEDBACK_SESSION_NAME, session.feedbackSessionName
        };
        
        ______TS("Typical Case: ensure Unpublish Action can be executed");
        
        makeFeedbackSessionPublished(session);
        
        InstructorFeedbackUnpublishAction unpublishAction = getAction(unpublishParams);
        RedirectResult result = (RedirectResult) unpublishAction.executeAndPostProcess();
        
        String expectedDestination = Const.ActionURIs.INSTRUCTOR_FEEDBACKS_PAGE + 
                "?message=The+feedback+session+has+been+unpublished." + 
                "&error=false" + 
                "&user=idOfInstructor1OfCourse1";
        assertEquals(expectedDestination, result.getDestinationWithParams());
        assertEquals(Const.StatusMessages.FEEDBACK_SESSION_UNPUBLISHED, result.getStatusMessage());
        assertFalse(result.isError);
        
        ______TS("Unpublished Case: ensure Unpublishing an unpublished session can be handled");
        
        makeFeedbackSessionUnpublished(session);
        
        unpublishAction = getAction(unpublishParams);
        result = (RedirectResult) unpublishAction.executeAndPostProcess();
        
        expectedDestination = Const.ActionURIs.INSTRUCTOR_FEEDBACKS_PAGE + 
                "?message=The+feedback+session+has+already+been+unpublished." + 
                "&error=true" + 
                "&user=idOfInstructor1OfCourse1";
        assertEquals(expectedDestination, result.getDestinationWithParams());
        assertEquals(Const.StatusMessages.FEEDBACK_SESSION_UNPUBLISHED_ALREADY, result.getStatusMessage());
        assertTrue(result.isError);
        
        makeFeedbackSessionPublished(session);
    }
    
    private void modifyFeedbackSessionPublishState(FeedbackSessionAttributes session, boolean isPublished) throws Exception {
        // startTime < endTime <= resultsVisibleFromTime
        Date startTime = TimeHelper.getDateOffsetToCurrentTime(-2);
        Date endTime = TimeHelper.getDateOffsetToCurrentTime(-1);
        Date resultsVisibleFromTimeForPublishedSession = TimeHelper.getDateOffsetToCurrentTime(-1);
        
        session.startTime = startTime;
        session.endTime = endTime;
        if(isPublished){
            session.resultsVisibleFromTime = resultsVisibleFromTimeForPublishedSession;
            assertTrue(session.isPublished());
        } else {
            session.resultsVisibleFromTime = Const.TIME_REPRESENTS_LATER;
            assertFalse(session.isPublished());
        }
        session.sentPublishedEmail = true;
        new FeedbackSessionsDb().updateFeedbackSession(session);
    }

    private void makeFeedbackSessionPublished(FeedbackSessionAttributes session) throws Exception {
        modifyFeedbackSessionPublishState(session, PUBLISHED);
    }
    
    private void makeFeedbackSessionUnpublished(FeedbackSessionAttributes session) throws Exception {
        modifyFeedbackSessionPublishState(session, UNPUBLISHED);
    }
    
    private InstructorFeedbackUnpublishAction getAction(String[] params){
        return (InstructorFeedbackUnpublishAction) gaeSimulation.getActionObject(uri, params);
    }
}
