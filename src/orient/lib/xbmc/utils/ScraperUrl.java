package orient.lib.xbmc.utils;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/*
 * XBMC code downloaded on: 4/7/14
 **/

public class ScraperUrl {

	public String m_xml;
	public String m_spoof; // for backwards compatibility only!
	public String strTitle;
	public String strId;
	public double relevance;
	public ArrayList<UrlEntry> m_url;

	// private final String CACHE_FILE_NAME = "response.txt";
	// File cacheDir;

	// public enum URLTYPES {
	// URL_TYPE_GENERAL, URL_TYPE_SEASON
	// };

	public ScraperUrl() {
		m_url = new ArrayList<UrlEntry>();
		relevance = 0;
	}

	public ScraperUrl(String strUrl) {
		m_url = new ArrayList<UrlEntry>();
		relevance = 0;

		ParseString(strUrl);
	}

	public void Clear() {
		m_url.clear();
		m_xml = null;
		m_spoof = null;
		relevance = 0;
	}

	public boolean Parse() {
		String strToParse = m_xml;
		m_xml = null;
		// return OldParseXml(strToParse);
		return ParseString(strToParse);
	}

	public boolean ParseElement(Element element) {

		if (element == null || element.getFirstChild() == null
				|| element.getFirstChild().getNodeValue() == null)
			return false;

		m_xml = XMLUtils.nodeToString(element);// pass in the root


		UrlEntry url = new UrlEntry();

		// url
		Node child = element.getFirstChild();// XMLHelper.getFirstChildElement(element);
		
		// to get rid of whitespace nodes i.e. \n
		while (child != null && child.getNodeName() == "#text" && child.getNodeValue().trim().length() == 0)
			child = child.getNextSibling();
		
		if (child != null) {
			
			url.m_url = child.getNodeValue();

			if (url.m_url == null || url.m_url.length() == 0)
				url.m_url = child.getNodeName();
			
			url.m_url = StringEscapeUtils.unescapeXml(url.m_url);
		}
		

		// spoof
		url.m_spoof = XMLUtils.getAttribute(element, "spoof");

		// cache
		url.m_cache = XMLUtils.getAttribute(element, "cache");

		// aspect
		url.m_aspect = XMLUtils.getAttribute(element, "aspect");

		// post
		String szPost = XMLUtils.getAttribute(element, "post");

		if (szPost != null && szPost.equals("yes"))
			url.m_post = true;
		else
			url.m_post = false;

		// post
		String szIsGz = XMLUtils.getAttribute(element, "gzip");

		if (szIsGz != null && szIsGz.equals("yes"))
			url.m_isgz = true;
		else
			url.m_isgz = false;

		// type & season
		String szType = XMLUtils.getAttribute(element, "type");

		url.m_type = URL_TYPE.GENERAL;
		url.m_season = -1;

		if (szType != null && szType.equals("season")) {
			url.m_type = URL_TYPE.SEASON;

			String szSeason = XMLUtils.getAttribute(element, "season");

			if (szSeason != null)
				url.m_season = Integer.parseInt(szSeason);
		}

		m_url.add(url);

		return true;
	}

	// Just to save code breaks
	public boolean ParseString(String strUrl) {

		Document doc;

		try {

			/*
			 * strUrl is coming from internal sources (usually generated by
			 * scraper or from database) so strUrl is always in UTF-8
			 */
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new InputSource(new StringReader(strUrl)));
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return false;
		}

		Element pElement = doc.getDocumentElement();

		if (pElement == null) {
			UrlEntry url = new UrlEntry();
			url.m_url = strUrl;
			m_url.add(url);
			m_xml = strUrl;
		} else {
			while (pElement != null) {
				ParseElement(pElement);
				pElement = XMLUtils.getNextSiblingElement(pElement,
						pElement.getNodeValue());
			}
		}

		return true;
	}

	public UrlEntry GetFirstThumb(String type) {

		for (UrlEntry temp : m_url) {
			if (temp.m_type == URL_TYPE.GENERAL
					&& (type == null || type.length() == 0 || type == "thumb" || temp.m_aspect == type))
				return temp;
		}

		UrlEntry result = new UrlEntry();
		result.m_type = URL_TYPE.GENERAL;
		result.m_post = false;
		result.m_isgz = false;
		result.m_season = -1;
		return result;
	}

	public UrlEntry GetSeasonThumb(int season, String type) {

		for (UrlEntry temp : m_url) {
			if (temp.m_type == URL_TYPE.SEASON
					&& temp.m_season == season
					&& (type == null || type.length() == 0 || type == "thumb" || temp.m_aspect == type))
				return temp;
		}

		UrlEntry result = new UrlEntry();
		result.m_type = URL_TYPE.GENERAL;
		result.m_post = false;
		result.m_isgz = false;
		result.m_season = -1;
		return result;
	}

	public int GetMaxSeasonThumb() {
		int maxSeason = 0;

		for (UrlEntry temp : m_url) {
			if (temp.m_type == URL_TYPE.SEASON && temp.m_season > 0
					&& temp.m_season > maxSeason)
				maxSeason = temp.m_season;
		}

		return maxSeason;
	}

	/*
	 * This method from xbmc has been broken up into multiple methods: Get,
	 * ExecuteHttpRequest, ProcessHttpResponse and SaveCache.
	 * 
	 * Also, to save contents to cache, the cache directory has to be set
	 * through SetCacheDir.
	 */
	public static String Get(UrlEntry scrURL, String cacheContext) {
		ScraperUrlGet urlGetter = new ScraperUrlGet(scrURL, cacheContext);

		return urlGetter.Get();
	}

	// XML format is of strUrls is:
	// <TAG><url>...</url>...</TAG> (parsed by ParseElement) or <url>...</url>
	// (ditto)
	public boolean ParseEpisodeGuide(String strUrls) {
		if (strUrls.length() == 0)
			return false;

		Document doc;

		try {

			/*
			 * strUrl is coming from internal sources (usually generated by
			 * scraper or from database) so strUrl is always in UTF-8
			 */
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new InputSource(new StringReader(strUrls)));
		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
			return false;
		}

		Element pElement = doc.getDocumentElement();

		if (pElement == null) {
			return false;
		}

		Element link = XMLUtils.getFirstChildElement(pElement, "episodeguide");

		if (link == null)
			return false;

		if (XMLUtils.getFirstChildElement(link, "url") != null) {
			link = XMLUtils.getFirstChildElement(link, "url");

			while (link != null) {
				ParseElement(link);
				link = XMLUtils.getNextSiblingElement(link, "url");
			}

		} else if (link.getFirstChild() != null
				&& link.getFirstChild().getNodeValue() != null) {
			ParseElement(link);
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	public String GetThumbURL(UrlEntry entry) {
		if (entry.m_spoof.length() == 0)
			return entry.m_url;

		return entry.m_url + "|Referer=" + URLEncoder.encode(entry.m_spoof);
	}

	public ArrayList<String> GetThumbURLs(ArrayList<String> thumbs,
			String type, int season) {
		for (UrlEntry temp : m_url) {
			if (temp.m_aspect == type || type.length() == 0 || type == "thumb"
					|| temp.m_aspect.length() == 0) {
				if ((temp.m_type == URL_TYPE.GENERAL && season == -1)
						|| (temp.m_type == URL_TYPE.SEASON && temp.m_season == season))
					thumbs.add(GetThumbURL(temp));
			}
		}

		return thumbs;
	}

}