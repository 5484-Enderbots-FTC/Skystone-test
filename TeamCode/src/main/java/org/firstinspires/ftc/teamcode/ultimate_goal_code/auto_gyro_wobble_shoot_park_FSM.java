/*
 * Copyright (c) 2020 OpenFTC Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.firstinspires.ftc.teamcode.ultimate_goal_code;

import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.bosch.JustLoggingAccelerationIntegrator;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.Acceleration;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvPipeline;

@Disabled
@Autonomous(name = "wobble shoot park w gyro", group = "auto")
public class auto_gyro_wobble_shoot_park_FSM extends LinearOpMode {
    ElapsedTime runtime = new ElapsedTime();
    ElapsedTime timer = new ElapsedTime();

    //OpenCV stuff
    OpenCvCamera webcam;
    RingStackDeterminationPipeline pipeline;

    //motors
    DcMotorEx mtrBL , mtrBR , mtrFL , mtrFR , mtrIntake, mtrWobble, mtrFlywheel;
    Servo svoWobble, svoMagLift, svoRingPush, svoForkHold;

    BNO055IMU imu;

    double globalAngle, power = .30, correction;
    Orientation lastAngles = new Orientation();
    Orientation angles;
    Acceleration gravity;

    State currentState;

    
    //constants
    private final double ticksPerInchCalibrated = 43.3305;

    double magDown = 0.85;
    double magUp = 0.58;
    double ringPushOut = 0.6;
    double ringPushIn = 0.75;
    double wobbleRelease = 0.37;
    double wobbleHold = 0.2;
    double forkHold = 0.8;
    double forkRelease = 0.7;

    public static class var{
        private static int RingStackIndentified = 0;
    }

    public enum Zone{
        A,
        B,
        C
    }
    Zone targetZone = Zone.A;

    public enum State{
        DETECT_RING_STACK,
        NO_RINGS,
        ONE_RING,
        FOUR_RINGS,
        STOP
    }

    @Override
    public void runOpMode() {

            //openCV config
            int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
            webcam = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
            pipeline = new RingStackDeterminationPipeline();
            webcam.setPipeline(pipeline);

            webcam.openCameraDeviceAsync(() -> webcam.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT)
            );

            //imu config
            BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();

            parameters.mode = BNO055IMU.SensorMode.IMU;
            parameters.angleUnit = BNO055IMU.AngleUnit.DEGREES;
            parameters.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
            parameters.calibrationDataFile = "AdafruitIMUCalibration.json";
            parameters.loggingEnabled = true;
            parameters.loggingTag = "IMU";
            parameters.accelerationIntegrationAlgorithm = new JustLoggingAccelerationIntegrator();

            imu = hardwareMap.get(BNO055IMU.class, "imu");
            imu.initialize(parameters);

            //motors
            mtrBL = hardwareMap.get(DcMotorEx.class, "mtrBL");
            mtrBL.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrBL.setDirection(DcMotorEx.Direction.REVERSE);

            mtrBR = hardwareMap.get(DcMotorEx.class, "mtrBR");
            mtrBR.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrBR.setDirection(DcMotorEx.Direction.FORWARD);

            mtrFL = hardwareMap.get(DcMotorEx.class, "mtrFL");
            mtrFL.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrFL.setDirection(DcMotorEx.Direction.REVERSE);

            mtrFR = hardwareMap.get(DcMotorEx.class, "mtrFR");
            mtrFR.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrFR.setDirection(DcMotorEx.Direction.FORWARD);

            mtrIntake = hardwareMap.get(DcMotorEx.class, "mtrIntake");
            mtrIntake.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrIntake.setDirection(DcMotorEx.Direction.FORWARD);

            mtrFlywheel = hardwareMap.get(DcMotorEx.class, "mtrFlywheel");
            mtrFlywheel.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.FLOAT);
            mtrFlywheel.setDirection(DcMotorEx.Direction.REVERSE);

            svoMagLift = hardwareMap.get(Servo.class,"svoMagLift");
            svoMagLift.setDirection(Servo.Direction.FORWARD);

            svoRingPush = hardwareMap.get(Servo.class,"svoRingPush");
            svoRingPush.setDirection(Servo.Direction.REVERSE);

            svoWobble = hardwareMap.get(Servo.class,"svoWobble");
            svoWobble.setDirection(Servo.Direction.FORWARD);

            svoForkHold = hardwareMap.get(Servo.class,"svoForkHold");
            svoForkHold.setDirection(Servo.Direction.FORWARD);

        // make sure the imu gyro is calibrated before continuing.
        while (!isStopRequested() && !imu.isGyroCalibrated())
        {
            sleep(50);
            idle();
        }

            telemetry.addData("Status", "Initialized");
            telemetry.addData("imu calib status", imu.getCalibrationStatus().toString());
            telemetry.addData("Status", "Run Time: " + runtime.toString());
            telemetry.addData("Analysis", pipeline.getAnalysis());
            telemetry.addData("Position", pipeline.position);
            telemetry.update();

            svoForkHold.setPosition(forkHold);
            svoWobble.setPosition(wobbleHold);
            svoRingPush.setPosition(ringPushIn);
            svoMagLift.setPosition(magDown);

            waitForStart();
            currentState = State.DETECT_RING_STACK;

            while (!isStopRequested() && opModeIsActive()) {


                if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.NONE)) {
                    telemetry.addLine("Zone A, no rings");
                    telemetry.update();
                } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.ONE)) {
                    telemetry.addLine("Zone B, one ring");
                    telemetry.update();
                } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.FOUR)) {
                    telemetry.addLine("Zone C, four rings");
                    telemetry.update();
                }

                switch (currentState) {

                    case DETECT_RING_STACK:
                        if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.NONE) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.A;
                            currentState = State.NO_RINGS;
                        } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.ONE) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.B;
                            currentState = State.ONE_RING;
                        } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.FOUR) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.C;
                            currentState = State.FOUR_RINGS;
                        }
                        break;
                    case NO_RINGS:
                        telemetry.addLine("Zone A, no rings");
                        telemetry.update();
                        //forward to shooting pos whereever the donde that is lol and shoot shoot
                        encoderForwardNoBrake(0.6,46);
                        encoderForward(0.2,15);
                        encoderStrafe(0.3,16);
                        svoMagLift.setPosition(magUp);
                        shootThree(0.6);
                        svoMagLift.setPosition(magDown);

                        // nav to zone a
                        encoderStrafe(0.3,32);
                        encoderForward(0.4,8);
                        svoWobble.setPosition(wobbleRelease);
                        waitFor(1);
                        //back up to second wobble (no rings so navigate however)

                        //180 spin spin

                        //drop it like its hot

                        //park
                        encoderForward(-0.4,-6);
                        encoderStrafe(-0.4,-28);
                        encoderForward(0.6,19);
                        currentState = State.STOP;

                        break;
                    case ONE_RING:
                        telemetry.addLine("Zone B, one ring");
                        telemetry.update();
                        //forward to shooting pos donde estas and brrrr
                        encoderForwardNoBrake(0.6,46);
                        encoderForward(0.2,15);
                        encoderStrafe(0.2,16);
                        svoMagLift.setPosition(magUp);
                        shootThree(0.6);
                        svoMagLift.setPosition(magDown);

                        //nav to zone b and rain drop drop top
                        encoderStrafe(0.2,4);
                        encoderForward(0.6,21);
                        svoWobble.setPosition(wobbleRelease);
                        waitFor(1);

                        //back up to second wobble

                        //scooch over and forward to the single ring and turn on the intake and yOink

                        //tHEn 180 woosh

                        //drip drop

                        //park
                        encoderForward(-0.6,-5);
                        currentState = State.STOP;
                        break;
                    case FOUR_RINGS:
                        telemetry.addLine("Zone C, four rings");
                        telemetry.update();
                        //forward to shooter babey skrrrAH
                        encoderForwardNoBrake(0.6,46);
                        encoderForward(0.2,15);
                        encoderStrafe(0.2,16);
                        svoMagLift.setPosition(magUp);
                        shootThree(0.6);
                        svoMagLift.setPosition(magDown);

                        //nav to zone c lol
                        encoderStrafe(0.3,32);
                        encoderForwardNoBrake(0.6,46);
                        encoderForward(0.6,6);
                        svoWobble.setPosition(wobbleRelease);
                        waitFor(1);

                        //then back that ass straight tf up along the wall then strafe at the right point to pick up wobble dos

                        //180 babey

                        //zone c again

                        //park
                        encoderForward(-0.6,-35);
                        currentState = State.STOP;
                        break;
                    case STOP:
                        brakeMotors();

                        break;
                }

                telemetry.addData("Analysis", pipeline.getAnalysis());
                telemetry.addData("Position", pipeline.position);
                telemetry.update();
            }
    }


    public static class RingStackDeterminationPipeline extends OpenCvPipeline
    {

        public enum RingPosition
        {
            FOUR,
            ONE,
            NONE
        }

        static final Scalar BLUE = new Scalar(0, 0, 255, 255);
        static final Scalar GREEN = new Scalar(0, 255, 0, 255);

        static final Point REGION1_TOPLEFT_ANCHOR_POINT = new Point(210,135);

        static final int REGION_WIDTH = 25;
        static final int REGION_HEIGHT = 30;

        final int FOUR_RING_THRESHOLD = 150;
        final int ONE_RING_THRESHOLD = 135;

        Point region1_pointA = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x,
                REGION1_TOPLEFT_ANCHOR_POINT.y);
        Point region1_pointB = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x + REGION_WIDTH,
                REGION1_TOPLEFT_ANCHOR_POINT.y + REGION_HEIGHT);

        Mat region1_Cb;
        Mat YCrCb = new Mat();
        Mat Cb = new Mat();
        int avg1;

        // Volatile since accessed by OpMode thread w/o synchronization
        private volatile RingPosition position = RingPosition.FOUR;

        void inputToCb(Mat input)
        {
            Imgproc.cvtColor(input, YCrCb, Imgproc.COLOR_RGB2YCrCb);
            Core.extractChannel(YCrCb, Cb, 1);
        }

        @Override
        public void init(Mat firstFrame)
        {
            inputToCb(firstFrame);

            region1_Cb = Cb.submat(new Rect(region1_pointA, region1_pointB));
        }

        @Override
        public Mat processFrame(Mat input)
        {
            inputToCb(input);

            avg1 = (int) Core.mean(region1_Cb).val[0];

            Imgproc.rectangle(
                    input,
                    region1_pointA,
                    region1_pointB,
                    BLUE,
                    2);

            position = RingPosition.FOUR;
            if(avg1 > FOUR_RING_THRESHOLD){
                var.RingStackIndentified = 1;
                position = RingPosition.FOUR;
            }else if (avg1 > ONE_RING_THRESHOLD){
                var.RingStackIndentified = 1;
                position = RingPosition.ONE;
            }else{
                var.RingStackIndentified = 1;
                position = RingPosition.NONE;
            }

            Imgproc.rectangle(
                    input,
                    region1_pointA,
                    region1_pointB,
                    GREEN,
                    -1);

            return input;
        }

        public int getAnalysis()
        {
            return avg1;
        }
    }
    private void waitFor(double waittime) {
        timer.reset();
        while (timer.seconds() < waittime) {
        }
    }

    private void pushARing(){
        svoRingPush.setPosition(ringPushOut);
        waitFor(0.5);
        svoRingPush.setPosition(ringPushIn);
    }
    private void shootThree(double inBetweenRingTime){
        mtrFlywheel.setPower(0.7);
        waitFor(1);
        pushARing();
        waitFor(inBetweenRingTime);
        pushARing();
        waitFor(inBetweenRingTime);
        pushARing();
        waitFor(inBetweenRingTime);
        mtrFlywheel.setPower(0);
        
    }
    private void resetEncoders() {
        mtrFR.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrFL.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrBR.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrBL.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
    }
    private void runToPosition() {
        mtrFR.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrFL.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrBR.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrBL.setMode(DcMotor.RunMode.RUN_TO_POSITION);
    }
    private void brakeMotors() {
        mtrFL.setPower(0);
        mtrFR.setPower(0);
        mtrBL.setPower(0);
        mtrBR.setPower(0);
    }
    private void mtrFRisBusy() {
        while (mtrFR.isBusy()){
        }
    }
    private void mtrBLisBusy() {
        while (mtrBL.isBusy()){
        }
    }
    private void runWithoutEncoder() {
        mtrFR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrFL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrBR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrBL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    private void forward(double power) {
        mtrBL.setPower(power);
        mtrBR.setPower(power);
        mtrFL.setPower(power);
        mtrFR.setPower(power);
        
        
    }
    private void forwardPosition(int distance_inches) {
        mtrBL.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
        mtrBR.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
        mtrFL.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
        mtrFR.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
    }
    private void encoderForward(double power, int distance_inches){
        resetEncoders();
        forwardPosition(-distance_inches);
        runToPosition();
        forward(power);
        mtrFRisBusy();
        brakeMotors();
        runWithoutEncoder();
    }
    private void encoderForwardNoBrake(double power, int distance_inches){
        resetEncoders();
        forwardPosition(-distance_inches);
        runToPosition();
        forward(power);
        mtrFRisBusy();
        runWithoutEncoder();
    }

    private void strafe(double power) {
        correction = checkDirection();

        telemetry.addData("1 imu heading", lastAngles.firstAngle);
        telemetry.addData("2 global heading", globalAngle);
        telemetry.addData("3 correction", correction);
        telemetry.update();

        mtrBL.setPower(power - correction);
        mtrBR.setPower(-power + correction);
        mtrFL.setPower(-power - correction);
        mtrFR.setPower(power + correction);
    }
    private void strafePosition(int distance_inches){
        mtrBL.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
        mtrBR.setTargetPosition(-distance_inches*(int)ticksPerInchCalibrated);
        mtrFL.setTargetPosition(-distance_inches*(int)ticksPerInchCalibrated);
        mtrFR.setTargetPosition(distance_inches*(int)ticksPerInchCalibrated);
    }
    private void encoderStrafe(double power, int distance_inches){
        resetEncoders();
        strafePosition(distance_inches);
        runToPosition();
        strafe(power);
        mtrFRisBusy();
        brakeMotors();
        runWithoutEncoder();
    }

    /**
     * Resets the cumulative angle tracking to zero.
     */
    private void resetAngle()
    {
        lastAngles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES);

        globalAngle = 0;
    }

    /**
     * Get current cumulative angle rotation from last reset.
     * @return Angle in degrees. + = left, - = right.
     */
    private double getAngle()
    {
        // We experimentally determined the Z axis is the axis we want to use for heading angle.
        // We have to process the angle because the imu works in euler angles so the Z axis is
        // returned as 0 to +180 or 0 to -180 rolling back to -179 or +179 when rotation passes
        // 180 degrees. We detect this transition and track the total cumulative angle of rotation.

        Orientation angles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES);

        double deltaAngle = angles.firstAngle - lastAngles.firstAngle;

        if (deltaAngle < -180)
            deltaAngle += 360;
        else if (deltaAngle > 180)
            deltaAngle -= 360;

        globalAngle += deltaAngle;

        lastAngles = angles;

        return globalAngle;
    }

    /**
     * See if we are moving in a straight line and if not return a power correction value.
     * @return Power adjustment, + is adjust left - is adjust right.
     */
    private double checkDirection()
    {
        // The gain value determines how sensitive the correction is to direction changes.
        // You will have to experiment with your robot to get small smooth direction changes
        // to stay on a straight line.
        double correction, angle, gain = .10;

        angle = getAngle();

        if (angle == 0)
            correction = 0;             // no adjustment.
        else
            correction = -angle;        // reverse sign of angle for correction.

        correction = correction * gain;

        return correction;
    }

    /**
     * Rotate left or right the number of degrees. Does not support turning more than 180 degrees.
     * @param degrees Degrees to turn, + is left - is right
     */
    private void rotate(int degrees, double power)
    {
        double  leftPower, rightPower;

        // restart imu movement tracking.
        resetAngle();

        // getAngle() returns + when rotating counter clockwise (left) and - when rotating
        // clockwise (right).

        if (degrees < 0)
        {   // turn right.
            leftPower = power;
            rightPower = -power;
        }
        else if (degrees > 0)
        {   // turn left.
            leftPower = -power;
            rightPower = power;
        }
        else return;

        // set power to rotate.
        //leftMotor.setPower(leftPower);
        //rightMotor.setPower(rightPower);

        // rotate until turn is completed.
        if (degrees < 0)
        {
            // On right turn we have to get off zero first.
            while (opModeIsActive() && getAngle() == 0) {}

            while (opModeIsActive() && getAngle() > degrees) {}
        }
        else    // left turn.
            while (opModeIsActive() && getAngle() < degrees) {}

        // turn the motors off.
        mtrBL.setPower(0);
        mtrBR.setPower(0);
        mtrFR.setPower(0);
        mtrFL.setPower(0);

        // wait for rotation to stop.
        sleep(1000);

        // reset angle tracking on new heading.
        resetAngle();
    }


}
