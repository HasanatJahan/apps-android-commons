package fr.free.nrw.commons;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.apache.commons.lang3.StringUtils;
import org.wikipedia.dataclient.mwapi.MwQueryPage;
import org.wikipedia.gallery.ExtMetadata;
import org.wikipedia.gallery.ImageInfo;
import org.wikipedia.page.PageTitle;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.free.nrw.commons.location.LatLng;
import fr.free.nrw.commons.utils.CommonsDateUtil;
import fr.free.nrw.commons.utils.MediaDataExtractorUtil;

@Entity
public class Media implements Parcelable {

    public static final Media EMPTY = new Media("");
    public static Creator<Media> CREATOR = new Creator<Media>() {
        @Override
        public Media createFromParcel(Parcel parcel) {
            return new Media(parcel);
        }

        @Override
        public Media[] newArray(int i) {
            return new Media[0];
        }
    };

    // Primary metadata fields
    @Nullable
    public Uri localUri;
    public String thumbUrl;
    public String imageUrl;
    public String filename;
    public String description; // monolingual description on input...
    public String discussion;
    long dataLength;
    public Date dateCreated;
    @Nullable public  Date dateUploaded;
    public int width;
    public int height;
    public String license;
    public String licenseUrl;
    public String creator;
    public ArrayList<String> categories; // as loaded at runtime?
    public boolean requestedDeletion;
    public HashMap<String, String> descriptions; // multilingual descriptions as loaded
    public HashMap<String, String> tags = new HashMap<>();
    @Nullable public  LatLng coordinates;

    /**
     * Provides local constructor
     */
    protected Media() {
        this.categories = new ArrayList<>();
        this.descriptions = new HashMap<>();
    }

    /**
     * Provides a minimal constructor
     *
     * @param filename Media filename
     */
    public Media(String filename) {
        this();
        this.filename = filename;
    }

    /**
     * Provide Media constructor
     * @param localUri Media URI
     * @param imageUrl Media image URL
     * @param filename Media filename
     * @param description Media description
     * @param dataLength Media date length
     * @param dateCreated Media creation date
     * @param dateUploaded Media date uploaded
     * @param creator Media creator
     */
    public Media(Uri localUri, String imageUrl, String filename, String description,
                 long dataLength, Date dateCreated, Date dateUploaded, String creator) {
        this();
        this.localUri = localUri;
        this.thumbUrl = imageUrl;
        this.imageUrl = imageUrl;
        this.filename = filename;
        this.description = description;
        this.dataLength = dataLength;
        this.dateCreated = dateCreated;
        this.dateUploaded = dateUploaded;
        this.creator = creator;
        this.categories = new ArrayList<>();
        this.descriptions = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public Media(Parcel in) {
        localUri = in.readParcelable(Uri.class.getClassLoader());
        thumbUrl = in.readString();
        imageUrl = in.readString();
        filename = in.readString();
        description = in.readString();
        dataLength = in.readLong();
        dateCreated = (Date) in.readSerializable();
        dateUploaded = (Date) in.readSerializable();
        creator = in.readString();
        tags = (HashMap<String, String>) in.readSerializable();
        width = in.readInt();
        height = in.readInt();
        license = in.readString();
        if (categories != null) {
            in.readStringList(categories);
        }
        descriptions = in.readHashMap(ClassLoader.getSystemClassLoader());
    }

    /**
     * Creating Media object from MWQueryPage.
     * Earlier only basic details were set for the media object but going forward,
     * a full media object(with categories, descriptions, coordinates etc) can be constructed using this method
     *
     * @param page response from the API
     * @return Media object
     */
    @Nullable
    public static Media from(MwQueryPage page) {
        ImageInfo imageInfo = page.imageInfo();
        if (imageInfo == null) {
            return null;
        }
        ExtMetadata metadata = imageInfo.getMetadata();
        if (metadata == null) {
            Media media = new Media(null, imageInfo.getOriginalUrl(),
                    page.title(), "", 0, null, null, null);
            if (!StringUtils.isBlank(imageInfo.getThumbUrl())) {
                media.setThumbUrl(imageInfo.getThumbUrl());
            }
            return media;
        }

        Media media = new Media(null,
                imageInfo.getOriginalUrl(),
                page.title(),
                "",
                0,
                safeParseDate(metadata.dateTime()),
                safeParseDate(metadata.dateTime()),
                getArtist(metadata)
        );

        if (!StringUtils.isBlank(imageInfo.getThumbUrl())) {
            media.setThumbUrl(imageInfo.getThumbUrl());
        }

        String language = Locale.getDefault().getLanguage();
        if (StringUtils.isBlank(language)) {
            language = "default";
        }

        media.setDescriptions(Collections.singletonMap(language, metadata.imageDescription()));
        media.setCategories(MediaDataExtractorUtil.extractCategoriesFromList(metadata.getCategories()));
        String latitude = metadata.getGpsLatitude();
        String longitude = metadata.getGpsLongitude();

        if (!StringUtils.isBlank(latitude) && !StringUtils.isBlank(longitude)) {
            LatLng latLng = new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude), 0);
            media.setCoordinates(latLng);
        }

        media.setLicenseInformation(metadata.licenseShortName(), metadata.licenseUrl());
        return media;
    }

    /**
     * This method extracts the Commons Username from the artist HTML information
     * @param metadata
     * @return
     */
    private static String getArtist(ExtMetadata metadata) {
        try {
            String artistHtml = metadata.artist();
            return artistHtml.substring(artistHtml.indexOf("title=\""), artistHtml.indexOf("\">"))
                    .replace("title=\"User:", "");
        } catch (Exception ex) {
            return "";
        }
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    /**
     * Gets tag of media
     * @param key Media key
     * @return Media tag
     */
    public Object getTag(String key) {
        return tags.get(key);
    }

    /**
     * Modifies( or creates a) tag of media
     * @param key Media key
     * @param value Media value
     */
    public void setTag(String key, String value) {
        tags.put(key, value);
    }

    /**
     * Gets media display title
     * @return Media title
     */
    @NonNull public String getDisplayTitle() {
        return filename != null ? getPageTitle().getDisplayTextWithoutNamespace().replaceFirst("[.][^.]+$", "") : "";
    }

    /**
     * Gets file page title
     * @return New media page title
     */
    @NonNull public PageTitle getPageTitle() {
        return Utils.getPageTitle(getFilename());
    }

    /**
     * Gets local URI
     * @return Media local URI
     */
    public Uri getLocalUri() {
        return localUri;
    }

    /**
     * Gets image URL
     * can be null.
     * @return Image URL
     */
    @Nullable
    public String getImageUrl() {
        return imageUrl;
    }

    /**
     * Gets the name of the file.
     * @return file name as a string
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the name of the file.
     * @param filename the new name of the file
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Sets the discussion of the file.
     * @param discussion
     */
    public void setDiscussion(String discussion) {
        this.discussion = discussion;
    }

    /**
     * Gets the file discussion as a string.
     * @return file discussion as a string
     */
    public String getDiscussion() {
        return discussion;
    }

    /**
     * Gets the file description.
     * @return file description as a string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the file description.
     * @param description the new description of the file
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the dataLength of the file.
     * @return file dataLength as a long
     */
    public long getDataLength() {
        return dataLength;
    }

    /**
     * Sets the dataLength of the file.
     * @param dataLength as a long
     */
    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    /**
     * Gets the creation date of the file.
     * @return creation date as a Date
     */
    public Date getDateCreated() {
        return dateCreated;
    }

    /**
     * Sets the creation date of the file.
     * @param date creation date as a Date
     */
    public void setDateCreated(Date date) {
        this.dateCreated = date;
    }

    /**
     * Gets the upload date of the file.
     * Can be null.
     * @return upload date as a Date
     */
    public @Nullable
    Date getDateUploaded() {
        return dateUploaded;
    }

    /**
     * Gets the name of the creator of the file.
     * @return creator name as a String
     */
    public String getCreator() {
        return creator;
    }

    /**
     * Sets the creator name of the file.
     * @param creator creator name as a string
     */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Gets the width of the media.
     * @return file width as an int
     */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the width of the media.
     * @param width file width as an int
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Gets the height of the media.
     * @return file height as an int
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the height of the media.
     * @param height file height as an int
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Gets the license name of the file.
     * @return license as a String
     */
    public String getLicense() {
        return license;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    /**
     * Sets the license name of the file.
     * @param license license name as a String
     */
    public void setLicenseInformation(String license, String licenseUrl) {
        this.license = license;

        if (!licenseUrl.startsWith("http://") && !licenseUrl.startsWith("https://")) {
            licenseUrl = "https://" + licenseUrl;
        }
        this.licenseUrl = licenseUrl;
    }

    /**
     * Gets the coordinates of where the file was created.
     * @return file coordinates as a LatLng
     */
    public @Nullable
    LatLng getCoordinates() {
        return coordinates;
    }

    /**
     * Sets the coordinates of where the file was created.
     * @param coordinates file coordinates as a LatLng
     */
    public void setCoordinates(@Nullable LatLng coordinates) {
        this.coordinates = coordinates;
    }

    /**
     * Gets the categories the file falls under.
     * @return file categories as an ArrayList of Strings
     */
    @SuppressWarnings("unchecked")
    public ArrayList<String> getCategories() {
        return (ArrayList<String>) categories.clone(); // feels dirty
    }

    /**
     * Sets the categories the file falls under.
     * </p>
     * Does not append: i.e. will clear the current categories
     * and then add the specified ones.
     * @param categories file categories as a list of Strings
     */
    public void setCategories(List<String> categories) {
        this.categories.clear();
        this.categories.addAll(categories);
    }

    /**
     * Modifies (or sets) media descriptions
     * @param descriptions Media descriptions
     */
    void setDescriptions(Map<String, String> descriptions) {
        this.descriptions.clear();
        this.descriptions.putAll(descriptions);
    }

    /**
     * Gets media description in preferred language
     * @param preferredLanguage Language preferred
     * @return Description in preferred language
     */
    public String getDescription(String preferredLanguage) {
        if (descriptions.containsKey(preferredLanguage)) {
            // See if the requested language is there.
            return descriptions.get(preferredLanguage);
        } else if (descriptions.containsKey("en")) {
            // Ah, English. Language of the world, until the Chinese crush us.
            return descriptions.get("en");
        } else if (descriptions.containsKey("default")) {
            // No languages marked...
            return descriptions.get("default");
        } else {
            // FIXME: return the first available non-English description?
            return "";
        }
    }

    @Nullable private static Date safeParseDate(String dateStr) {
        try {
            return CommonsDateUtil.getIso8601DateFormatShort().parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Method of Parcelable interface
     * @return zero
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creates a way to transfer information between two or more
     * activities.
     * @param parcel Instance of Parcel
     * @param flags Parcel flag
     */
    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(localUri, flags);
        parcel.writeString(thumbUrl);
        parcel.writeString(imageUrl);
        parcel.writeString(filename);
        parcel.writeString(description);
        parcel.writeLong(dataLength);
        parcel.writeSerializable(dateCreated);
        parcel.writeSerializable(dateUploaded);
        parcel.writeString(creator);
        parcel.writeSerializable(tags);
        parcel.writeInt(width);
        parcel.writeInt(height);
        parcel.writeString(license);
        parcel.writeStringList(categories);
        parcel.writeMap(descriptions);
    }

    /**
     * Set requested deletion to true
     */
    public void setRequestedDeletion(){
        requestedDeletion = true;
    }

    /**
     * Get the value of requested deletion
     * @return boolean requestedDeletion
     */
    public boolean getRequestedDeletion(){
        return requestedDeletion;
    }

    /**
     * Sets the license name of the file.
     *
     * @param license license name as a String
     */
    public void setLicense(String license) {
        this.license = license;
    }
}
