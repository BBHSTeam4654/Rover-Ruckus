package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.robot.Robot;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.matrices.VectorF;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackable;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackableDefaultListener;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;

import static org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.XYZ;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.YZX;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesReference.EXTRINSIC;
import static org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer.CameraDirection.BACK;
import static org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer.CameraDirection.FRONT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VuforiaNav {
    // Constants
    private static final int MAX_TARGETS = 4;
    private static final double CLOSE_ENOUGH = 20;

    // Camera selection - alt is FRONT
    private static final VuforiaLocalizer.CameraDirection CAMERA_CHOICE = VuforiaLocalizer.CameraDirection.BACK;

    // Camera config
    final int CAMERA_FORWARD_DISPLACEMENT = 110; // eg: Camera is 110 mm in front of robot center
    final int CAMERA_VERTICAL_DISPLACEMENT = 200; // eg: Camera is 200 mm above ground
    final int CAMERA_LEFT_DISPLACEMENT = 0; // eg: Camera is ON the robot's center line

    public static final double YAW_GAIN = 0.018; // Rate of response for heading error
    public static final double LATERAL_GAIN = 0.0027; // Rate of response for off-axis error
    public static final double AXIAL_GAIN = 0.0017; // Rate of response for target distance errors

    private LinearOpMode linearOpMode;
    private OpenGLMatrix lastLocation = null;

    // Nav data
    private boolean targetVisible = false;
    private String targetName = null;
    private double robotX; // X displacement from target center
    private double robotY;
    private double robotZ; // Height displacement
    private double robotBearing;
    private double relativeBearing;
    private double targetBearing;
    private double targetRange;

    // Field dimensions
    private static final float mmPerInch = 25.4f;
    private static final float mmFTCFieldWidth = (12 * 6) * mmPerInch;
    private static final float mmTargetHeight = (6) * mmPerInch;

    // Vuforia license key
    private static final String VUFORIA_KEY = "AV7cAYn/////AAAAGXDR1Nv900lOoewPO1Nq3ypDBIfk+d8X+UJOgVQZn5ZvQIY5Y4yGL6DVf24bEoMOVLCq5sZXPs9937r2zpeSZQaaaJbxeWggveVuvccsVlBdR38brId6fIRi/ssxtkUpVppCaRDO1N6K7IVbAJWrhpv1rG2DqTcS51znxjEYDE34AN6sNkurIq/qs0tLfvI+lx5VYRKdqh5LwnVt2HnpdX836kSbAN/1wnupzlLSKHcVPF9zlmRjCXrHduW8ikVefKAPGNCEzaDj4D+X+YM9iaHj9H8qN23bbaT81Ze3g5WwrXsb6dsX1N3+FqeXbiEUB02lXsmGwtvCJI89xutgPzlDAHqerduaLS2WZbL3oVyS";

    VuforiaLocalizer vuforia;
    VuforiaTrackables targets;
    List<VuforiaTrackable> allTrackables = new ArrayList<VuforiaTrackable>();
    HashMap<String, Double> navData = new HashMap<String, Double>();

    public void initVuforia(LinearOpMode opmode) {
        linearOpMode = opmode;
        // Camera Preview Paramater object creation
        int cameraMonitorViewId = linearOpMode.hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id",
                linearOpMode.hardwareMap.appContext.getPackageName());
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters(cameraMonitorViewId);

        parameters.vuforiaLicenseKey = VUFORIA_KEY;
        parameters.cameraDirection = CAMERA_CHOICE;

        // Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

        // Load the data sets that for the trackable objects. These particular data
        // sets are stored in the 'assets' part of our application.
        targets = this.vuforia.loadTrackablesFromAsset("RoverRuckus");

        // Load data sets for tracking objects. Stored in assets
        VuforiaTrackable blueRover = targets.get(0);
        blueRover.setName("Blue-Rover");
        VuforiaTrackable redFootprint = targets.get(1);
        redFootprint.setName("Red-Footprint");
        VuforiaTrackable frontCraters = targets.get(2);
        frontCraters.setName("Front-Craters");
        VuforiaTrackable backSpace = targets.get(3);
        backSpace.setName("Back-Space");

        allTrackables.addAll(targets);

        /*
         * Starting from Red Alliance Station looking to the center, - X axis runs from
         * Left to Right - Y axis runs from the Red Alliance Station to the Blue
         * Allience Station - Z axis runs from the floor and up Default location is
         * origin at the center of the field, rotated facing up
         */

        // Placing BlueRover target into the middle of the blue perimeter wall
        OpenGLMatrix blueRoverLocationOnField = OpenGLMatrix.translation(0, mmFTCFieldWidth, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 0));
        blueRover.setLocation(blueRoverLocationOnField); // Set Matrix locations to blueRover

        // Placing redFootprint target onto the middle of the red perimeter wall
        OpenGLMatrix redFootprintLocationOnField = OpenGLMatrix.translation(0, -mmFTCFieldWidth, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 180));
        redFootprint.setLocation(redFootprintLocationOnField);

        // Placing FrontCraters target onto the middle of the front perimeter wall
        OpenGLMatrix frontCratersLocationOnField = OpenGLMatrix.translation(-mmFTCFieldWidth, 0, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 90));
        frontCraters.setLocation(frontCratersLocationOnField);

        // Placing BackSpace target onto the middle of the back perimeter wall
        OpenGLMatrix backSpaceLocationOnField = OpenGLMatrix.translation(mmFTCFieldWidth, 0, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, -90));
        backSpace.setLocation(backSpaceLocationOnField);

        // Set phone location
        OpenGLMatrix phoneLocationOnRobot = OpenGLMatrix
                .translation(CAMERA_FORWARD_DISPLACEMENT, CAMERA_LEFT_DISPLACEMENT, CAMERA_VERTICAL_DISPLACEMENT)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, YZX, DEGREES, CAMERA_CHOICE == FRONT ? 90 : -90, 0,
                        0));

        for (VuforiaTrackable trackable : allTrackables) {
            ((VuforiaTrackableDefaultListener) trackable.getListener()).setPhoneInformation(phoneLocationOnRobot,
                    parameters.cameraDirection);
        }

        linearOpMode.telemetry.addData(">>", "Navigation Intialized");
        linearOpMode.telemetry.update();
    }

    // cycles through all targets until one is found.
    public boolean targetsVisible() {
        int targetTestID = 0;
        updateNav();
        while ((targetTestID < MAX_TARGETS) && !targetVisible) {
            targetTestID++;
        }
        return (targetVisible);
    }

    // Update all navigation data if target visible
    public void updateNav() {
        targetVisible = false;
        for (VuforiaTrackable trackable : allTrackables) {
            if (((VuforiaTrackableDefaultListener) trackable.getListener()).isVisible()) {
                linearOpMode.telemetry.addData("Visible Target", trackable.getName());
                targetVisible = true;

                // getUpdatedRobotLocation() will return null if no new information is available
                // since
                // the last time that call was made, or if the trackable is not currently
                // visible.
                OpenGLMatrix robotLocationTransform = ((VuforiaTrackableDefaultListener) trackable.getListener())
                        .getUpdatedRobotLocation();
                if (robotLocationTransform != null) {
                    lastLocation = robotLocationTransform;
                }
                break;
            }
        }

        // Provide feedback as to where the robot is located (if we know).
        if (targetVisible) {
            VectorF trans = lastLocation.getTranslation();
            Orientation rot = Orientation.getOrientation(lastLocation, EXTRINSIC, XYZ, DEGREES);

            robotX = trans.get(0);
            robotY = trans.get(1);
            robotZ = trans.get(2);

            // Robot bearing (in +vc CCW cartesian system) is defined by the standard Matrix
            // z rotation
            robotBearing = rot.thirdAngle;

            // target range is based on distance from robot position to origin.
            targetRange = Math.hypot(robotX, robotY);

            // target bearing is based on angle formed between the X axis to the target
            // range line
            targetBearing = Math.toDegrees(-Math.asin(robotY / targetRange));

            // Target relative bearing is the target Heading relative to the direction the
            // robot is pointing.
            relativeBearing = targetBearing - robotBearing;

            // Refresh data onto map
            navData.put("robotX", robotX);
            navData.put("robotY", robotY);
            navData.put("robotZ", robotZ);
            navData.put("robotBearing", robotBearing);
            navData.put("targetRange", targetRange);
            navData.put("targetBearing", targetBearing);
            navData.put("relativeBearing", relativeBearing);
        }
        addNavTelemetry();
    }

    public void activateNavigation() {
        if (targets != null)
            targets.activate();
    }

    public void addNavTelemetry() {
        if (targetVisible) {
            // Display the current visible target name, robot info, target info, and
            // required robot action.
            linearOpMode.telemetry.addData("Visible", targetName);
            linearOpMode.telemetry.addData("Robot", "[X]:[Y]:[Z] (B) [%5.0fmm]:[%5.0fmm]:[%5.0fmm] (%4.0f°)", robotX,
                    robotZ, robotY, robotBearing);
            linearOpMode.telemetry.addData("Target", "[R] (B):(RB) [%5.0fmm] (%4.0f°):(%4.0f°)", targetRange,
                    targetBearing, relativeBearing);
            linearOpMode.telemetry.addData("- Turn    ", "%s %4.0f°", relativeBearing < 0 ? ">>> CW " : "<<< CCW",
                    Math.abs(relativeBearing));
            linearOpMode.telemetry.addData("- Strafe  ", "%s %5.0fmm", robotY < 0 ? "LEFT" : "RIGHT", Math.abs(robotY));
            linearOpMode.telemetry.addData("- Distance", "%5.0fmm", Math.abs(robotX));
        } else {
            linearOpMode.telemetry.addData("Visible", "- - - -");
        }
        linearOpMode.telemetry.update();
    }

    // check if is at target X Pos
    public boolean atXPos(double targetXPos) {
        boolean closeEnough;
        updateNav();
        closeEnough = (Math.abs(robotX - targetXPos) < CLOSE_ENOUGH);
        return closeEnough;
    }

    // check if is at target Y Pos
    public boolean atYPos(double targetYPos) {
        boolean closeEnough;
        updateNav();
        closeEnough = (Math.abs(robotY - targetYPos) < CLOSE_ENOUGH);
        return closeEnough;
    }

    // check if is at target Z Pos
    public boolean atZPos(double targetZPos) {
        boolean closeEnough;
        updateNav();
        closeEnough = (Math.abs(robotZ - targetZPos) < CLOSE_ENOUGH);
        return closeEnough;
    }

    public VectorF checkPos() {
        updateNav();
        VectorF translation = lastLocation.getTranslation();
        return translation;
    }

    public Orientation checkOri() {
        updateNav();
        Orientation rot = Orientation.getOrientation(lastLocation, AxesReference.EXTRINSIC, AxesOrder.XYZ,
                AngleUnit.DEGREES);
        return rot;
    }

    public HashMap getNavigation() {
        updateNav();
        return navData;
    }
}