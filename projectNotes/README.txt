PhotoSync
David Wu


(work in progress)

Syncs photos on your phone to google drive


Install time:
	Get all photos from DCIM folder
	Upload all photos to google drive folder
		Ask user to choose or create google driver folder to sync to
	Create file of already uploaded images

Runtime:
	Every time a picture is taken (register broadcast receiver?),
	 	upload to google drive (if file hasn't already been uploaded)
	Upon deletion of picture in google drive, delete photo off phone



https://console.developers.google.com/apis/credentials?project=genial-insight-121902
https://drive.google.com/drive/folders/0B47XzXi6CHlyWXZTZ1hYWl9Bd0U
https://developers.google.com/drive/android/folders
https://github.com/googledrive/android-demos/blob/master/app/src/main/java/com/google/android/gms/drive/sample/demo/CreateFileActivity.java
https://github.com/googledrive/android-demos/blob/master/app/src/main/java/com/google/android/gms/drive/sample/demo/CreateFileInFolderActivity.java
https://github.com/dxwu/PhotoSync

